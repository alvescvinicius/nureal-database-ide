package com.nureal.ide.ui;

import com.nureal.ide.core.autocomplete.SqlCompletionProvider;
import com.nureal.ide.core.format.SqlFormatter;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableInfo;
import com.nureal.ide.core.sql.SqlStatementLocator;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Uma aba de edicao SQL: editor com syntax highlighting e autocomplete.
 * Cada aba tem seu proprio editor, compartilhando o mesmo provider de sugestoes.
 */
public class SqlEditorPane extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int BASE_FONT_SIZE = 14;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 42;

	private final RSyntaxTextArea textArea;
    private final Supplier<SqlFormatter> formatterSupplier;
    private int fontSize = BASE_FONT_SIZE;
    private String fontFamily; // null/vazio = escolha automatica

    // Id estavel desta aba (UUID), definido pelo chamador na criacao — usado
    // para religar corretamente os resultados salvos (ver Conexao#tabResults)
    // independente da POSICAO da aba na lista, que pode mudar (fechar/reabrir
    // abas em outra ordem).
    private final String tabId;

    // Id da query salva (ver SavedQueryStore) a que esta aba esta "ligada" —
    // null enquanto a aba nunca foi salva OU foi aberta sem vir de uma query
    // salva. Uma vez definido, salvar de novo SOBRESCREVE em vez de perguntar
    // o titulo (ver MainWindow#onSaveQuery).
    private String savedQueryId;

    private final SearchContext searchContext = new SearchContext();
    private JPanel findBar;
    private JTextField findField;
    private JTextField replaceField;
    private JToggleButton matchCaseBtn;
    private JToggleButton wholeWordBtn;
    private JLabel findStatus;

    /**
     * Devolve o schema atualmente conectado (ou {@code null}, sem conexao/
     * schema selecionado) — usado pela navegacao interativa do editor
     * (secao 8 do pedido "Navegacao Inteligente e Interativa": hover com
     * tooltip, CTRL+Clique, etc.) para saber se uma palavra em negrito no
     * texto e de fato um objeto real do banco (tabela/view/procedure/
     * function/trigger), e nao so um alias. Chamado sob demanda (a cada
     * movimento do mouse), nunca guardado em cache aqui — reflete sempre o
     * schema mais atual de {@code MainWindow}, mesmo que o usuario troque de
     * conexao/schema com a aba aberta.
     */
    private final Supplier<SchemaInfo> schemaSupplier;

    /**
     * Chamado quando o usuario da CTRL+Clique sobre um objeto de banco
     * reconhecido no editor (secao 8.3 do pedido "Navegacao Inteligente e
     * Interativa") — {@code kind} e "TABLE"/"VIEW"/"PROCEDURE"/"FUNCTION"/
     * "TRIGGER" (mesmos valores usados pela arvore de objetos, ver
     * {@code MainWindow.ObjNode#kind()}); {@code table} vem preenchido so
     * para TABLE/VIEW, {@code null} para os demais. {@code MainWindow}
     * decide o que fazer (abrir a tela de propriedades, no caso).
     */
    @FunctionalInterface
    interface ObjectOpenHandler {
        void open(String kind, String name, TableInfo table);
    }

    private final ObjectOpenHandler onOpenObject;

    /**
     * Chamado quando o CURSOR (nao o mouse) passa a estar sobre um objeto de
     * banco reconhecido no editor (secao 8.4 — sincronizar a arvore de
     * objetos com o cursor). So dispara quando o objeto MUDA (evita
     * notificar a cada tecla dentro do mesmo nome) e so quando o cursor
     * ENTRA em cima de um objeto — sair para texto comum nao limpa a
     * selecao da arvore (evita ela "piscando" toda hora).
     */
    @FunctionalInterface
    interface CaretObjectListener {
        void onObjectUnderCaret(String kind, String name);
    }

    private final CaretObjectListener onCaretObject;

    /**
     * Chamado quando o usuario aperta ALT+Seta-esquerda no editor (secao 8.6
     * do pedido "Navegacao Inteligente e Interativa") — pede pra
     * {@code MainWindow} voltar ao objeto anterior no historico de navegacao
     * (ver {@code MainWindow#navigateBack}). Sem nocao de "objeto atual" aqui
     * no editor: quem guarda o historico e quem decide o que fazer e o
     * chamador.
     */
    private final Runnable onNavigateBack;

    public SqlEditorPane(String tabId, SqlCompletionProvider provider, Runnable onRun,
            Supplier<SqlFormatter> formatterSupplier, String fontFamily, Supplier<SchemaInfo> schemaSupplier,
            ObjectOpenHandler onOpenObject, CaretObjectListener onCaretObject, Runnable onNavigateBack) {
        super(new BorderLayout());

        this.tabId = tabId;
        this.formatterSupplier = formatterSupplier;
        this.fontFamily = fontFamily;
        this.schemaSupplier = schemaSupplier;
        this.onOpenObject = onOpenObject;
        this.onCaretObject = onCaretObject;
        this.onNavigateBack = onNavigateBack;

        textArea = new RSyntaxTextArea(20, 80) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {
                EditorObjectHit hit = resolveObjectAt(viewToModel2D(e.getPoint()));
                return (hit != null) ? tooltipHtmlFor(hit) : null;
            }
        };
        javax.swing.ToolTipManager.sharedInstance().registerComponent(textArea);
        // setSyntaxEditingStyle continua chamado primeiro: e o nome de
        // estilo guardado na PROPRIA RSyntaxTextArea (nao no documento) que
        // o code folding usa pra achar o FoldParser certo (ver SqlFoldParser
        // em App.java) — so DEPOIS trocamos o TokenMaker do documento pelo
        // nosso, instanciado diretamente (nao mais via TokenMakerFactory,
        // que so cria por reflexao com construtor sem argumentos e nao
        // deixaria a instancia "conhecer" esta RSyntaxTextArea — ver
        // SqlHighlightTokenMaker#getTokenList para o motivo de precisar
        // disso: negritar aliases usados ANTES do FROM que os define).
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        ((RSyntaxDocument) textArea.getDocument()).setSyntaxStyle(new SqlHighlightTokenMaker(textArea));
        textArea.setCodeFoldingEnabled(true);
        textArea.setTabSize(2);
        textArea.setText("");
        textArea.setFont(pickEditorFont(fontFamily, BASE_FONT_SIZE));
        // Nomes de tabela/view/procedure/alias (ver SqlHighlightTokenMaker,
        // que os reclassifica para DATA_TYPE) devem ficar em NEGRITO, mas
        // SEM mudar de cor — o padrao de fabrica do RSyntaxTextArea pinta
        // DATA_TYPE de um teal proprio, que o usuario relatou cansar a
        // vista ("queria apenas que ficasse negrito, nao de outra cor").
        // Sobrescreve so o FONT (bold, mesma familia/tamanho do editor) e
        // deixa a cor null — igual o estilo de IDENTIFIER (texto comum),
        // que usa a cor padrao do componente. changeBaseFont() do proprio
        // RSyntaxTextArea (chamado automaticamente por setFont(), zoom
        // incluso) atualiza o tamanho desta fonte junto com o resto do
        // editor, ja que ela deriva da MESMA fonte base.
        SyntaxScheme scheme = textArea.getSyntaxScheme();
        scheme.setStyle(TokenTypes.DATA_TYPE, new Style(null, null, textArea.getFont().deriveFont(Font.BOLD)));
        textArea.setAntiAliasingEnabled(true);
        textArea.setFractionalFontMetricsEnabled(true);
        textArea.setPaintTabLines(true);
        textArea.setHighlightCurrentLine(true);
        // Fundo do editor em cinza bem claro (nao branco puro) p/ cansar menos a vista
        textArea.setBackground(new Color(0xF6, 0xF7, 0xF9));
        // Realces translucidos (verde da marca) para um visual mais suave
        textArea.setCurrentLineHighlightColor(new Color(0x05, 0x96, 0x69, 22));
        textArea.setSelectionColor(new Color(0x05, 0x96, 0x69, 60));
        textArea.setMarkAllHighlightColor(new Color(0x22, 0xC5, 0x5E, 90));
        installCurrentStatementHighlight(textArea);
        installObjectHover(textArea);
        installCaretObjectSync(textArea);
        installReferenceHighlight(textArea);

        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(200);
        // Mesmo com UMA unica sugestao, mostra o popup em vez de inserir
        // automaticamente: a insercao so ocorre quando o usuario escolhe
        // (clique, Enter ou Ctrl+Espaco), para nao atrapalhar a digitacao.
        ac.setAutoCompleteSingleChoices(false);
        ac.install(textArea);

        // Executa: Ctrl+Enter (preferido) e F5
        textArea.getActionMap().put("run-sql", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onRun.run();
            }
        });
        textArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "run-sql");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run-sql");

        // Ctrl+Shift+F formata (beautifier)
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control shift F"), "format-sql");
        textArea.getActionMap().put("format-sql", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                formatText();
            }
        });

        // Ctrl+F / Ctrl+H abrem a MESMA barra (localizar + substituir ja
        // aparecem juntos, sem aba separada) — mas cada um foca o campo que
        // o usuario espera pelo atalho: Ctrl+F -> "Localizar", Ctrl+H ->
        // "Substituir" (convencao de Word/VS Code/etc.). Antes os dois
        // focavam sempre "Localizar", entao Ctrl+H nao se comportava
        // diferente de Ctrl+F — corrigido aqui.
        AbstractAction showFind = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showFindBar(false);
            }
        };
        AbstractAction showReplace = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showFindBar(true);
            }
        };
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control F"), "show-find");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control H"), "show-replace");
        textArea.getActionMap().put("show-find", showFind);
        textArea.getActionMap().put("show-replace", showReplace);

        // Caixa: Ctrl+U / Ctrl+Shift+U -> MAIUSCULAS ; Ctrl+L / Ctrl+Shift+L -> minusculas
        AbstractAction toUpper = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeCase(true);
            }
        };
        AbstractAction toLower = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeCase(false);
            }
        };
        textArea.getActionMap().put("to-upper", toUpper);
        textArea.getActionMap().put("to-lower", toLower);
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control U"), "to-upper");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control shift U"), "to-upper");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control L"), "to-lower");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control shift L"), "to-lower");
        // Reforco: os bindings de InputMap/ActionMap acima as vezes NAO
        // disparavam (usuario relatou Ctrl+U/Ctrl+L sem nenhum efeito numa
        // selecao grande). Causa provavel: o Swing notifica TODOS os
        // KeyListener's registrados no editor (inclusive o da biblioteca de
        // autocomplete, instalada logo acima via ac.install(textArea)) ANTES
        // de processar os key bindings do InputMap — se qualquer um deles
        // consumir o evento (e.consume()) por algum motivo proprio, o
        // binding de InputMap simplesmente nunca chega a rodar, sem erro
        // nenhum, exatamente o sintoma relatado. Um KeyListener proprio,
        // registrado diretamente, sempre RODA (listeners nao impedem uns aos
        // outros de serem chamados so por consumir o evento) — chamando
        // changeCase(...) daqui direto, o atalho funciona independente do
        // que mais estiver escutando teclas no editor.
        textArea.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!e.isControlDown() || e.isAltDown()) {
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_U) {
                    changeCase(true);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_L) {
                    changeCase(false);
                    e.consume();
                }
            }
        });

        // Zoom: Ctrl + '=' / '+' / numpad+  aumenta; Ctrl + '-' diminui; Ctrl+0 reseta
        textArea.getActionMap().put("zoom-in", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { zoom(+1); }
        });
        textArea.getActionMap().put("zoom-out", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { zoom(-1); }
        });
        textArea.getActionMap().put("zoom-reset", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { setFontSize(BASE_FONT_SIZE); }
        });
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl), "zoom-in");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ctrl), "zoom-in");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ctrl), "zoom-in");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ctrl), "zoom-out");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ctrl), "zoom-out");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_0, ctrl), "zoom-reset");

        // Navegacao (secao 8.6 do pedido "Navegacao Inteligente e
        // Interativa"): F12 = ir para definicao do objeto sob o CURSOR (mesmo
        // destino do CTRL+Clique da secao 8.3, so que sem precisar do mouse);
        // ALT+Seta-esquerda = voltar ao objeto anterior no historico mantido
        // por quem chama (ver #onNavigateBack). CTRL+Hover ja funciona sem
        // nada extra aqui: o tooltip da secao 8.2 (getToolTipText, ver acima)
        // aparece em qualquer hover sobre um objeto reconhecido, com ou sem
        // CTRL pressionado.
        textArea.getActionMap().put("goto-definition", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onOpenObject == null) {
                    return;
                }
                EditorObjectHit hit = resolveObjectAt(textArea.getCaretPosition());
                if (hit != null) {
                    onOpenObject.open(hit.kind(), hit.name(), hit.table());
                }
            }
        });
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), "goto-definition");

        textArea.getActionMap().put("navigate-back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onNavigateBack != null) {
                    onNavigateBack.run();
                }
            }
        });
        textArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "navigate-back");

        RTextScrollPane scroll = new RTextScrollPane(textArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        // Gutter (numeros de linha) num cinza levemente mais fechado que o editor
        scroll.getGutter().setBackground(new Color(0xEC, 0xEE, 0xF1));
        scroll.getGutter().setBorderColor(new Color(0xE0, 0xE3, 0xE7));
        // Ctrl + roda do mouse = zoom; sem Ctrl, repassa ao scroll (rola normalmente).
        // IMPORTANTE: ao adicionar um MouseWheelListener no textArea, o Swing para de
        // propagar a roda para o scroll pane -> por isso precisamos repassar manualmente.
        textArea.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                zoom(e.getWheelRotation() < 0 ? +1 : -1);
            } else {
                for (var l : scroll.getMouseWheelListeners()) {
                    l.mouseWheelMoved(e);
                }
            }
        });
        add(buildBreadcrumbBar(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildFindBar(), BorderLayout.SOUTH);
        installBreadcrumbSync(textArea);
    }

    /** Cor do destaque de fundo da instrucao atual — ver {@link #installCurrentStatementHighlight}. */
    private static final Color CURRENT_STATEMENT_BG = new Color(0x64, 0x74, 0x8B, 16);

    /**
     * Destaque sutil de fundo para TODA a instrucao SQL onde o cursor esta
     * no momento (secao 8.1 do pedido "Navegacao Inteligente e Interativa",
     * inspirado em como IDEs como IntelliJ realcam o bloco de codigo atual)
     * — atualiza a cada movimento do cursor (inclusive digitando, ja que
     * digitar move o cursor). Reusa o MESMO calculo de limites de instrucao
     * do destacador de sintaxe ({@link SqlStatementLocator}), entao os dois
     * sempre concordam sobre onde uma instrucao comeca/termina.
     *
     * So repinta quando a instrucao MUDA (compara com os limites da ultima
     * atualizacao) — mover o cursor dentro da MESMA instrucao nao teria
     * efeito visual nenhum mesmo, entao evitamos o trabalho a toa a cada
     * tecla digitada.
     */
    private static void installCurrentStatementHighlight(RSyntaxTextArea textArea) {
        Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(CURRENT_STATEMENT_BG);
        Object[] currentTag = { null };
        int[] lastBounds = { -1, -1 };
        Runnable update = () -> {
            Highlighter highlighter = textArea.getHighlighter();
            int[] bounds = SqlStatementLocator.boundsAt(textArea.getText(), textArea.getCaretPosition());
            if (bounds[0] == lastBounds[0] && bounds[1] == lastBounds[1]) {
                return;
            }
            lastBounds[0] = bounds[0];
            lastBounds[1] = bounds[1];
            if (currentTag[0] != null) {
                highlighter.removeHighlight(currentTag[0]);
                currentTag[0] = null;
            }
            if (bounds[1] > bounds[0]) {
                try {
                    currentTag[0] = highlighter.addHighlight(bounds[0], bounds[1], painter);
                } catch (BadLocationException ex) {
                    // documento mudando bem na hora (edicao concorrente com o
                    // repaint) — sem problema, so fica sem destacar desta vez;
                    // o proximo movimento do cursor tenta de novo.
                }
            }
        };
        textArea.addCaretListener(e -> update.run());
    }

    /** Cor do sublinhado ao passar o mouse sobre um objeto do banco — ver {@link #installObjectHover}. */
    private static final Color OBJECT_HOVER_UNDERLINE = new Color(0x33, 0x41, 0x55);

    /**
     * Um objeto de banco (tabela/view/procedure/function/trigger) encontrado
     * sob o cursor do mouse no editor — ver {@link #resolveObjectAt}. Secao
     * 8.2 do pedido "Navegacao Inteligente e Interativa": so existe um "hit"
     * quando a palavra em negrito sob o mouse bate com um nome de objeto
     * REAL do schema conectado (nao um alias qualquer, que tambem fica em
     * negrito mas nao e "clicavel"/navegavel).
     *
     * @param table preenchido so para TABLE/VIEW (para mostrar a contagem de
     *              colunas no tooltip); {@code null} para os demais tipos.
     */
    private record EditorObjectHit(String kind, String name, int startOffset, int endOffset, TableInfo table) {
    }

    /**
     * Liga o hover interativo sobre objetos do banco no editor (secao 8.2):
     * sublinhado discreto + cursor de mao ao passar o mouse sobre uma
     * tabela/view/procedure/function/trigger de verdade (validado contra o
     * schema atualmente conectado, via {@link #schemaSupplier} — sem
     * conexao/schema, nada e reconhecido como objeto, so texto normal). O
     * tooltip em si e servido por {@code getToolTipText(MouseEvent)},
     * sobrescrito na criacao do {@code textArea} (ver construtor), que
     * tambem chama {@link #resolveObjectAt}.
     */
    private void installObjectHover(RSyntaxTextArea textArea) {
        Highlighter.HighlightPainter underline = new UnderlineHighlightPainter(OBJECT_HOVER_UNDERLINE);
        Object[] tag = { null };
        textArea.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Highlighter highlighter = textArea.getHighlighter();
                if (tag[0] != null) {
                    highlighter.removeHighlight(tag[0]);
                    tag[0] = null;
                }
                EditorObjectHit hit = resolveObjectAt(textArea.viewToModel2D(e.getPoint()));
                if (hit != null) {
                    textArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    try {
                        tag[0] = highlighter.addHighlight(hit.startOffset(), hit.endOffset(), underline);
                    } catch (BadLocationException ignored) {
                        // documento mudando na hora: sem problema, so fica sem sublinhar
                    }
                } else {
                    textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                }
            }
        });
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                if (tag[0] != null) {
                    textArea.getHighlighter().removeHighlight(tag[0]);
                    tag[0] = null;
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // CTRL+Clique sobre um objeto reconhecido (secao 8.3): abre a
                // tela de propriedades — mesmo destino de duplo-clique na
                // arvore de objetos, so que a partir do editor. So dispara se
                // o mouse estiver EXATAMENTE sobre um objeto de verdade (o
                // mesmo criterio do hover/sublinhado acima), nunca sobre
                // texto qualquer.
                if (!e.isControlDown() || javax.swing.SwingUtilities.isRightMouseButton(e) || onOpenObject == null) {
                    return;
                }
                EditorObjectHit hit = resolveObjectAt(textArea.viewToModel2D(e.getPoint()));
                if (hit != null) {
                    onOpenObject.open(hit.kind(), hit.name(), hit.table());
                }
            }
        });
    }

    /**
     * Sincroniza a arvore de objetos com a posicao do CURSOR (secao 8.4): a
     * cada movimento do cursor (clique, digitacao, setas), se ele esta agora
     * sobre um objeto de banco reconhecido e DIFERENTE do ultimo notificado,
     * avisa {@link #onCaretObject}. Sair de cima de um objeto para texto
     * comum nao notifica nada — a arvore so "acompanha para frente", nunca
     * desseleciona sozinha (ver javadoc do proprio {@link CaretObjectListener}).
     */
    private void installCaretObjectSync(RSyntaxTextArea textArea) {
        String[] lastNotified = { null };
        textArea.addCaretListener(e -> {
            if (onCaretObject == null) {
                return;
            }
            EditorObjectHit hit = resolveObjectAt(textArea.getCaretPosition());
            if (hit == null) {
                return;
            }
            String key = hit.kind() + ":" + hit.name().toUpperCase(Locale.ROOT);
            if (key.equals(lastNotified[0])) {
                return;
            }
            lastNotified[0] = key;
            onCaretObject.onObjectUnderCaret(hit.kind(), hit.name());
        });
    }

    /** Cor do destaque de referencias (tabela + alias) — ver {@link #installReferenceHighlight}. */
    private static final Color REFERENCE_HIGHLIGHT_BG = new Color(0xB8, 0x86, 0x0B, 55);

    /**
     * Destaca TODAS as ocorrencias do objeto (tabela/view/...) E do alias sob
     * o cursor, em qualquer lugar da instrucao atual (secao 8.5 do pedido
     * "Navegacao Inteligente e Interativa") — funciona nos dois sentidos:
     * posicionar o cursor no nome da tabela destaca tambem o alias, e
     * vice-versa. Reusa {@link SqlHighlightTokenMaker#scanReferenceGroups} (a
     * mesma pre-varredura que decide o que fica em negrito) pra descobrir
     * quais nomes "andam juntos", e {@link SqlHighlightTokenMaker#findWordOffsets}
     * pra achar TODAS as posicoes de cada um deles no texto.
     *
     * Independente de {@link #resolveObjectAt}/{@link #classify}: aqui nao
     * importa se a palavra bate com um objeto REAL do schema conectado — um
     * alias sozinho (que nunca teria "hit" em 8.2/8.3/8.4) tambem dispara o
     * destaque de referencias, contanto que ele pertenca a um grupo
     * identificado por {@code scanReferenceGroups}.
     */
    private void installReferenceHighlight(RSyntaxTextArea textArea) {
        Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(REFERENCE_HIGHLIGHT_BG);
        List<Object> tags = new ArrayList<>();
        String[] lastKey = { null };
        textArea.addCaretListener(e -> {
            Highlighter highlighter = textArea.getHighlighter();
            String full = textArea.getText();
            int offset = textArea.getCaretPosition();
            String word = wordAt(full, offset);
            int[] bounds = SqlStatementLocator.boundsAt(full, offset);
            SqlHighlightTokenMaker.ReferenceGroup group = (word == null) ? null
                    : findGroupContaining(SqlHighlightTokenMaker.scanReferenceGroups(full, bounds[0], bounds[1]),
                            word.toUpperCase(Locale.ROOT));
            String key = (group == null) ? null : (bounds[0] + ":" + bounds[1] + ":" + group.names());
            if (java.util.Objects.equals(key, lastKey[0])) {
                return;
            }
            lastKey[0] = key;
            for (Object tag : tags) {
                highlighter.removeHighlight(tag);
            }
            tags.clear();
            if (group == null) {
                return;
            }
            for (int[] wordBounds : SqlHighlightTokenMaker.findWordOffsets(full, bounds[0], bounds[1], group.names())) {
                try {
                    tags.add(highlighter.addHighlight(wordBounds[0], wordBounds[1], painter));
                } catch (BadLocationException ignored) {
                    // documento mudando na hora: so nao destaca esta ocorrencia
                }
            }
        });
    }

    private static SqlHighlightTokenMaker.ReferenceGroup findGroupContaining(
            List<SqlHighlightTokenMaker.ReferenceGroup> groups, String word) {
        for (SqlHighlightTokenMaker.ReferenceGroup group : groups) {
            if (group.names().contains(word)) {
                return group;
            }
        }
        return null;
    }

    /** Palavra ({@code [A-Za-z0-9_]+}) que cobre {@code offset} em {@code full}, ou {@code null} se nao houver. */
    private static String wordAt(String full, int offset) {
        int[] span = wordSpanAt(full, offset);
        return (span == null) ? null : full.substring(span[0], span[1]);
    }

    /** Limites {@code [inicio, fim)} da palavra que cobre {@code offset} em {@code full}, ou {@code null} se nao houver. */
    private static int[] wordSpanAt(String full, int offset) {
        int n = full.length();
        if (offset < 0 || offset > n) {
            return null;
        }
        int s = offset;
        int e = offset;
        while (s > 0 && isWordChar(full.charAt(s - 1))) {
            s--;
        }
        while (e < n && isWordChar(full.charAt(e))) {
            e++;
        }
        return (s == e) ? null : new int[] {s, e};
    }

    /** Cor do texto do breadcrumb — ver {@link #buildBreadcrumbBar}. */
    private static final Color BREADCRUMB_FG = new Color(0x60, 0x6B, 0x7A);

    private JLabel breadcrumbLabel;

    /**
     * Barra fina acima do editor mostrando o contexto do cursor (secao 8.7 do
     * pedido "Navegacao Inteligente e Interativa"): {@code Schema > Tabela >
     * Coluna} quando o cursor esta sobre uma referencia de coluna, {@code
     * Schema > Tabela} sobre so a tabela/alias, ou {@code Procedure > nome}
     * (sem schema) sobre uma chamada de procedure/function/trigger — ver
     * {@link #computeBreadcrumb}. Vazio quando nao ha nada reconhecivel sob o
     * cursor (texto comum, palavra-chave, numero, etc.).
     */
    private JComponent buildBreadcrumbBar() {
        breadcrumbLabel = new JLabel(" ");
        breadcrumbLabel.setForeground(BREADCRUMB_FG);
        breadcrumbLabel.setFont(breadcrumbLabel.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        bar.setBackground(new Color(0xEC, 0xEE, 0xF1));
        bar.add(breadcrumbLabel, BorderLayout.WEST);
        return bar;
    }

    /** Atualiza o breadcrumb a cada movimento do cursor — so troca o texto do JLabel quando ele de fato muda. */
    private void installBreadcrumbSync(RSyntaxTextArea textArea) {
        String[] lastText = { null };
        textArea.addCaretListener(e -> {
            String text = computeBreadcrumb(textArea.getCaretPosition());
            String shown = (text == null || text.isBlank()) ? " " : text;
            if (!shown.equals(lastText[0])) {
                lastText[0] = shown;
                breadcrumbLabel.setText(shown);
            }
        });
    }

    /**
     * Monta o texto do breadcrumb para o cursor em {@code offset} — ver
     * javadoc de {@link #buildBreadcrumbBar}. Reusa a mesma deteccao de
     * palavra do destaque de referencias (secao 8.5) e, quando a palavra sob
     * o cursor faz parte de uma referencia qualificada ({@code alias.coluna}
     * ou {@code alias.<cursor aqui>}), tambem identifica a coluna.
     */
    private String computeBreadcrumb(int offset) {
        SchemaInfo schema = (schemaSupplier != null) ? schemaSupplier.get() : null;
        String schemaName = (schema != null) ? schema.name() : null;
        String full = textArea.getText();
        int[] span = wordSpanAt(full, offset);
        if (span == null) {
            return schemaName;
        }
        String rawWord = full.substring(span[0], span[1]);
        String upperWord = rawWord.toUpperCase(Locale.ROOT);
        String qualifier = null;
        String column = null;
        if (span[1] < full.length() && full.charAt(span[1]) == '.') {
            // cursor sobre o QUALIFICADOR (alias/tabela) de "qualificador.coluna"
            int[] nextSpan = wordSpanAt(full, span[1] + 1);
            if (nextSpan != null) {
                qualifier = rawWord;
                column = full.substring(nextSpan[0], nextSpan[1]);
            }
        }
        if (qualifier == null && span[0] > 0 && full.charAt(span[0] - 1) == '.') {
            // cursor sobre a COLUNA de "qualificador.coluna"
            int[] prevSpan = wordSpanAt(full, span[0] - 1);
            if (prevSpan != null) {
                qualifier = full.substring(prevSpan[0], prevSpan[1]);
                column = rawWord;
            }
        }
        if (qualifier == null) {
            // palavra solta, sem "." adjacente: so interessa se for, ela mesma,
            // um objeto reconhecido (tabela/view/alias/procedure/function/trigger)
            if (schema != null) {
                for (String p : schema.procedures()) {
                    if (p.equalsIgnoreCase(rawWord)) {
                        return "Procedure > " + p;
                    }
                }
                for (String f : schema.functions()) {
                    if (f.equalsIgnoreCase(rawWord)) {
                        return "Function > " + f;
                    }
                }
                for (String tg : schema.triggers()) {
                    if (tg.equalsIgnoreCase(rawWord)) {
                        return "Trigger > " + tg;
                    }
                }
            }
            if (SqlHighlightTokenMaker.KEYWORDS.contains(upperWord) || SqlHighlightTokenMaker.FUNCTIONS.contains(upperWord)) {
                return schemaName; // palavra-chave/funcao SQL: sem contexto de objeto
            }
            qualifier = rawWord;
        }
        String tableName = matchRealTable(schema, qualifier);
        if (tableName == null) {
            // qualificador nao bate direto com nenhuma tabela/view: tenta
            // resolver como ALIAS via os mesmos grupos de referencia do
            // destaque de 8.5 (tabela + alias juntos).
            int[] bounds = SqlStatementLocator.boundsAt(full, offset);
            List<SqlHighlightTokenMaker.ReferenceGroup> groups =
                    SqlHighlightTokenMaker.scanReferenceGroups(full, bounds[0], bounds[1]);
            SqlHighlightTokenMaker.ReferenceGroup group = findGroupContaining(groups, qualifier.toUpperCase(Locale.ROOT));
            if (group != null) {
                for (String candidate : group.names()) {
                    String match = matchRealTable(schema, candidate);
                    if (match != null) {
                        tableName = match;
                        break;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (schemaName != null) {
            sb.append(schemaName).append(" > ");
        }
        sb.append((tableName != null) ? tableName : qualifier);
        if (column != null) {
            sb.append(" > ").append(column);
        }
        return sb.toString();
    }

    /** Nome (com a grafia original do banco) da tabela/view do {@code schema} que bate com {@code candidate}, ou {@code null}. */
    private static String matchRealTable(SchemaInfo schema, String candidate) {
        if (schema == null || candidate == null) {
            return null;
        }
        for (TableInfo t : schema.tables()) {
            if (t.name().equalsIgnoreCase(candidate)) {
                return t.name();
            }
        }
        for (TableInfo v : schema.views()) {
            if (v.name().equalsIgnoreCase(candidate)) {
                return v.name();
            }
        }
        return null;
    }

    /**
     * Resolve o objeto de banco (se houver) sob o offset {@code offset} do
     * documento: encontra o TOKEN da linha que cobre esse offset e, so se
     * ele estiver marcado {@link TokenTypes#DATA_TYPE} (negrito — nome de
     * tabela/view/procedure/function/trigger OU alias, ver
     * {@link SqlHighlightTokenMaker}), verifica se o texto bate com um
     * objeto DE VERDADE do schema conectado no momento. Alias (que tambem
     * ficam em negrito) simplesmente nao batem com nenhuma lista do schema e
     * voltam {@code null} aqui — sem tooltip/cursor/sublinhado pra eles
     * (isso e papel da secao 8.5, destaque de referencias, nao 8.2).
     */
    private EditorObjectHit resolveObjectAt(int offset) {
        if (offset < 0 || schemaSupplier == null) {
            return null;
        }
        SchemaInfo schema = schemaSupplier.get();
        if (schema == null) {
            return null;
        }
        try {
            int line = textArea.getLineOfOffset(offset);
            Token tok = ((RSyntaxDocument) textArea.getDocument()).getTokenListForLine(line);
            while (tok != null) {
                if (tok.isPaintable() && tok.getType() == TokenTypes.DATA_TYPE && tok.containsPosition(offset)) {
                    String word = tok.getLexeme();
                    return classify(schema, word, tok.getOffset(), tok.getEndOffset());
                }
                tok = tok.getNextToken();
            }
        } catch (BadLocationException ignored) {
            // offset invalido (documento mudou bem na hora): sem objeto
        }
        return null;
    }

    private static EditorObjectHit classify(SchemaInfo schema, String word, int start, int end) {
        for (TableInfo t : schema.tables()) {
            if (t.name().equalsIgnoreCase(word)) {
                return new EditorObjectHit("TABLE", t.name(), start, end, t);
            }
        }
        for (TableInfo v : schema.views()) {
            if (v.name().equalsIgnoreCase(word)) {
                return new EditorObjectHit("VIEW", v.name(), start, end, v);
            }
        }
        for (String p : schema.procedures()) {
            if (p.equalsIgnoreCase(word)) {
                return new EditorObjectHit("PROCEDURE", p, start, end, null);
            }
        }
        for (String f : schema.functions()) {
            if (f.equalsIgnoreCase(word)) {
                return new EditorObjectHit("FUNCTION", f, start, end, null);
            }
        }
        for (String tg : schema.triggers()) {
            if (tg.equalsIgnoreCase(word)) {
                return new EditorObjectHit("TRIGGER", tg, start, end, null);
            }
        }
        return null;
    }

    private String tooltipHtmlFor(EditorObjectHit hit) {
        SchemaInfo schema = (schemaSupplier != null) ? schemaSupplier.get() : null;
        StringBuilder sb = new StringBuilder("<html><b>").append(hit.name()).append("</b><br>")
                .append(prettyKind(hit.kind()));
        if (schema != null) {
            sb.append("<br>Schema: ").append(schema.name());
        }
        if (hit.table() != null) {
            sb.append("<br>Colunas: ").append(hit.table().columns().size());
        }
        sb.append("</html>");
        return sb.toString();
    }

    private static String prettyKind(String kind) {
        return switch (kind) {
            case "TABLE" -> "Tabela";
            case "VIEW" -> "Visualizacao";
            case "PROCEDURE" -> "Procedure";
            case "FUNCTION" -> "Function";
            case "TRIGGER" -> "Trigger";
            default -> kind;
        };
    }

    /** Sublinhado discreto sob um trecho de texto — ver {@link #installObjectHover}. */
    private static final class UnderlineHighlightPainter implements Highlighter.HighlightPainter {

        private final Color color;

        UnderlineHighlightPainter(Color color) {
            this.color = color;
        }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r0 = c.modelToView2D(p0).getBounds();
                Rectangle r1 = c.modelToView2D(p1).getBounds();
                g.setColor(color);
                if (r0.y == r1.y) {
                    int y = r0.y + r0.height - 2;
                    g.drawLine(r0.x, y, r1.x, y);
                }
            } catch (BadLocationException ignored) {
                // offset invalido (documento mudou na hora): so nao desenha desta vez
            }
        }
    }

    public RSyntaxTextArea textArea() {
        return textArea;
    }

    /** Id estavel desta aba (UUID), definido na criacao pelo chamador. */
    String tabId() {
        return tabId;
    }

    /** Usa a selecao, se houver; caso contrario, o texto inteiro. */
    public String currentSql() {
        String selected = textArea.getSelectedText();
        String sql = (selected != null && !selected.isBlank()) ? selected : textArea.getText();
        return sql.trim();
    }

    /** Verdadeiro se ha texto selecionado (entao currentSql() roda so a selecao). */
    public boolean hasSelection() {
        String selected = textArea.getSelectedText();
        return selected != null && !selected.isBlank();
    }

    /** Texto INTEIRO da aba (ignora selecao) — usado ao salvar como query. */
    public String fullText() {
        return textArea.getText();
    }

    /** Id da query salva a que esta aba esta ligada, ou {@code null} se nenhuma. */
    public String getSavedQueryId() {
        return savedQueryId;
    }

    public void setSavedQueryId(String savedQueryId) {
        this.savedQueryId = savedQueryId;
    }

    /** Ajusta o tamanho da fonte do editor (preservando o peso semibold). */
    public void setFontSize(int size) {
        fontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
        textArea.setFont(textArea.getFont().deriveFont((float) fontSize));
    }

    /** Troca a familia da fonte do editor, preservando o tamanho atual (zoom). */
    public void setFontFamily(String family) {
        this.fontFamily = family;
        textArea.setFont(pickEditorFont(family, fontSize));
    }

    public String fontFamily() {
        return fontFamily;
    }

    private void zoom(int delta) {
        setFontSize(fontSize + delta);
    }

    /**
     * Formata o SQL (beautifier). Se houver selecao, formata apenas a selecao;
     * caso contrario, formata o texto inteiro da aba.
     */
    public void formatText() {
        // Limpa marcacoes de "localizar todos" (Find) ANTES de trocar o texto:
        // elas guardam offsets do documento antigo, e o RSyntaxTextArea
        // continua tentando desenha-las depois do setText()/replaceSelection()
        // — como os offsets nao existem mais no texto novo, viram retangulos
        // tracejados em posicoes erradas (eram percebidos como "quadradinhos
        // verdes" sobre o texto recem-formatado).
        clearMarks();

        SqlFormatter formatter = formatterSupplier.get();
        String selected = textArea.getSelectedText();
        if (selected != null && !selected.isBlank()) {
            textArea.replaceSelection(formatter.format(selected));
            return;
        }
        String all = textArea.getText();
        if (all == null || all.isBlank()) {
            return;
        }
        int caret = textArea.getCaretPosition();
        String formatted = formatter.format(all);
        textArea.setText(formatted);
        textArea.setCaretPosition(Math.min(caret, formatted.length()));
    }

    // ---------- Localizar / Substituir ----------

    private JComponent buildFindBar() {
        findField = new JTextField(22);
        replaceField = new JTextField(22);
        findStatus = new JLabel();
        findStatus.setForeground(new Color(0x6B7280));

        matchCaseBtn = new JToggleButton("Aa");
        matchCaseBtn.setToolTipText("Diferenciar maiusculas/minusculas");
        wholeWordBtn = new JToggleButton("W");
        wholeWordBtn.setToolTipText("Palavra inteira");

        Color iconColor = new Color(0x6B7280);
        JButton prev = new JButton(Icons.get(IconType.CHEVRON_LEFT, 12, iconColor));
        prev.setToolTipText("Anterior (Shift+Enter)");
        prev.addActionListener(e -> findPrevious());
        JButton next = new JButton(Icons.get(IconType.CHEVRON_RIGHT, 12, iconColor));
        next.setToolTipText("Proximo (Enter)");
        next.addActionListener(e -> findNext());
        JButton replaceOne = new JButton("Substituir");
        replaceOne.addActionListener(e -> replaceOne());
        JButton replaceAll = new JButton("Substituir tudo");
        replaceAll.addActionListener(e -> replaceAll());
        JButton close = new JButton(Icons.get(IconType.CLOSE, 12, iconColor));
        close.setToolTipText("Fechar (Esc)");
        close.addActionListener(e -> hideFindBar());

        findField.addActionListener(e -> findNext());
        replaceField.addActionListener(e -> replaceOne());
        bindKey(findField, "shift ENTER", this::findPrevious);
        bindKey(findField, "ESCAPE", this::hideFindBar);
        bindKey(replaceField, "ESCAPE", this::hideFindBar);

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.add(new JLabel("Localizar:"));
        row1.add(findField);
        row1.add(prev);
        row1.add(next);
        row1.add(matchCaseBtn);
        row1.add(wholeWordBtn);
        row1.add(findStatus);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.add(new JLabel("Substituir:"));
        row2.add(replaceField);
        row2.add(replaceOne);
        row2.add(replaceAll);
        row2.add(close);

        findBar = new JPanel();
        findBar.setLayout(new BoxLayout(findBar, BoxLayout.Y_AXIS));
        findBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE5E7EB)),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        findBar.add(row1);
        findBar.add(row2);
        findBar.setVisible(false);
        return findBar;
    }

    private static void bindKey(JComponent c, String keyStroke, Runnable action) {
        c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyStroke), keyStroke);
        c.getActionMap().put(keyStroke, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /**
     * Mostra a barra de localizar/substituir (as duas linhas ja aparecem
     * juntas — nao ha um "modo" separado) e foca o campo correspondente ao
     * atalho usado.
     *
     * @param focusReplace {@code true} quando veio de Ctrl+H (foca "Substituir");
     *                     {@code false} quando veio de Ctrl+F (foca "Localizar").
     */
    private void showFindBar(boolean focusReplace) {
        String sel = textArea.getSelectedText();
        if (sel != null && !sel.isEmpty() && !sel.contains("\n")) {
            findField.setText(sel);
        }
        findBar.setVisible(true);
        revalidate();
        JTextField target = focusReplace ? replaceField : findField;
        target.requestFocusInWindow();
        target.selectAll();
    }

    private void hideFindBar() {
        findBar.setVisible(false);
        clearMarks();
        revalidate();
        textArea.requestFocusInWindow();
    }

    /** Limpa o destaque de "marcar todos" sem mexer no texto. */
    private void clearMarks() {
        SearchEngine.markAll(textArea, new SearchContext());
    }

    private void configureSearch(boolean forward) {
        searchContext.setSearchFor(findField.getText());
        searchContext.setReplaceWith(replaceField.getText());
        searchContext.setMatchCase(matchCaseBtn.isSelected());
        searchContext.setWholeWord(wholeWordBtn.isSelected());
        searchContext.setRegularExpression(false);
        searchContext.setSearchForward(forward);
        searchContext.setMarkAll(true);
    }

    private void findNext() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(true);
        SearchResult result = SearchEngine.find(textArea, searchContext);
        if (!result.wasFound()) {
            // wrap: recomeca do inicio
            textArea.setCaretPosition(0);
            result = SearchEngine.find(textArea, searchContext);
        }
        updateStatus(result);
    }

    private void findPrevious() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(false);
        SearchResult result = SearchEngine.find(textArea, searchContext);
        if (!result.wasFound()) {
            // wrap: recomeca do fim
            textArea.setCaretPosition(textArea.getDocument().getLength());
            result = SearchEngine.find(textArea, searchContext);
        }
        updateStatus(result);
    }

    private void replaceOne() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(true);
        SearchResult result = SearchEngine.replace(textArea, searchContext);
        if (!result.wasFound()) {
            textArea.setCaretPosition(0);
            result = SearchEngine.replace(textArea, searchContext);
        }
        updateStatus(result);
    }

    private void replaceAll() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(true);
        SearchResult result = SearchEngine.replaceAll(textArea, searchContext);
        findStatus.setText(result.getCount() + " substituicao(oes)");
    }

    private void updateStatus(SearchResult result) {
        int marked = result.getMarkedCount();
        if (!result.wasFound() && marked == 0) {
            findStatus.setText("Nenhum resultado");
        } else {
            findStatus.setText(marked + " ocorrencia(s)");
        }
    }

    // ---------- Fonte e caixa ----------

    /** Familias "encorpadas" (peso medio/semibold) — usadas direto, sem peso sintetico. */
    private static final String[] HEAVY_FONTS = {
            "JetBrains Mono Medium", "JetBrainsMono Medium",
            "Cascadia Code SemiBold", "Cascadia Mono SemiBold",
            "Fira Code Medium", "Source Code Pro Medium", "IBM Plex Mono Medium"};

    /**
     * Fontes monoespacadas candidatas, na ordem de preferencia. As 3
     * primeiras sao as recomendadas para a Nureal IDE (JetBrains Mono por
     * ter x-height alta e otima distincao 0/O; Fira Code pelas ligaduras de
     * codigo; Consolas/SF Mono por serem sobrias e nativas do SO).
     */
    private static final String[] REGULAR_FONTS = {
            "JetBrains Mono", "Fira Code", "SF Mono", "Consolas",
            "Cascadia Code", "Cascadia Mono", "Iosevka", "IBM Plex Mono", "Hack",
            "Source Code Pro", "Roboto Mono", "Ubuntu Mono", "Menlo",
            "DejaVu Sans Mono", "Liberation Mono", "Monaco", "Courier New"};

    /** Fontes (entre as candidatas acima) de fato instaladas neste sistema. */
    public static List<String> availableEditorFonts() {
        Set<String> available = installedFamilies();
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String family : REGULAR_FONTS) {
            if (available.contains(family) && seen.add(family)) {
                result.add(family);
            }
        }
        return result;
    }

    private static Set<String> installedFamilies() {
        return new HashSet<>(Arrays.asList(GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    }

    /**
     * Escolhe a fonte do editor no tamanho dado, aplicando um peso SEMIBOLD
     * (sintetico) por cima quando necessario, para deixar o texto mais
     * encorpado e legivel — independente de existir uma variante "Medium" no
     * SO. Se {@code preferredFamily} for informada e estiver instalada, ela
     * e usada; caso contrario, cai na deteccao automatica (melhor disponivel).
     */
    private static Font pickEditorFont(String preferredFamily, int size) {
        Set<String> available = installedFamilies();

        if (preferredFamily != null && !preferredFamily.isBlank()
                && available.contains(preferredFamily)) {
            Font base = new Font(preferredFamily, Font.PLAIN, size);
            boolean alreadyHeavy = Arrays.asList(HEAVY_FONTS).contains(preferredFamily);
            return alreadyHeavy ? base
                    : base.deriveFont(Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD));
        }

        for (String family : HEAVY_FONTS) {
            if (available.contains(family)) {
                return new Font(family, Font.PLAIN, size);
            }
        }
        Font base = new Font(Font.MONOSPACED, Font.PLAIN, size);
        for (String family : REGULAR_FONTS) {
            if (available.contains(family)) {
                base = new Font(family, Font.PLAIN, size);
                break;
            }
        }
        // peso semibold sintetico: deixa qualquer fonte mais encorpada e legivel
        return base.deriveFont(Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD));
    }

    /**
     * Converte para MAIUSCULAS (upper=true) ou minusculas a selecao atual; se
     * nao houver selecao, usa a palavra sob o cursor.
     */
    private void changeCase(boolean upper) {
        int start = textArea.getSelectionStart();
        int end = textArea.getSelectionEnd();
        String text = textArea.getText();
        if (start == end) {
            int caret = textArea.getCaretPosition();
            int s = caret;
            int e = caret;
            while (s > 0 && isWordChar(text.charAt(s - 1))) {
                s--;
            }
            while (e < text.length() && isWordChar(text.charAt(e))) {
                e++;
            }
            if (s == e) {
                return;
            }
            start = s;
            end = e;
        }
        String selected = text.substring(start, end);
        String replaced = upper
                ? selected.toUpperCase(Locale.ROOT)
                : selected.toLowerCase(Locale.ROOT);
        if (replaced.equals(selected)) {
            return;
        }
        textArea.setSelectionStart(start);
        textArea.setSelectionEnd(end);
        textArea.replaceSelection(replaced);
        textArea.setSelectionStart(start);
        textArea.setSelectionEnd(start + replaced.length());
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
