package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.IconTheme;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import com.formdev.flatlaf.FlatLaf;
import com.nureal.ide.modulos.autocomplete.infraestrutura.SqlCompletionProviderRSyntax;
import com.nureal.ide.modulos.editorsql.infraestrutura.EditorUndoManager;
import com.nureal.ide.core.format.SqlFormatter;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;
import com.nureal.ide.core.sql.SqlStatementLocator;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Theme;
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
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.io.IOException;
import java.io.InputStream;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

    // Guardados para poder REAPLICAR as cores quando o usuario alterna
    // claro/escuro com a aba ja aberta (ver #applyEditorPalette/#applyGutterPalette,
    // chamados de novo por #refreshTheme) — sem isto so abas NOVAS pegariam
    // o tema certo, abas ja abertas ficariam presas na cor de quando foram
    // criadas.
    private RTextScrollPane scrollPane;
    private JComponent breadcrumbBar;

    /**
     * Desfazer/refazer deste editor — ver {@link EditorUndoManager}. Ligado
     * diretamente no {@link javax.swing.text.Document}, independente do
     * undo manager interno do RSyntaxTextArea (que fica sem uso: nada mais
     * chama {@code textArea.undoLastAction()}/{@code redoLastAction()} —
     * Ctrl+Z/Ctrl+Y e o menu de contexto abaixo passam a usar SO este).
     */
    private final EditorUndoManager undoManager;

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

    // Nome do ESQUEMA a que esta aba "pertence" dentro da conexao ativa (ou
    // null, aba ainda sem esquema definido) — definido na criacao da aba
    // (herda o esquema aberto no momento) e atualizado sempre que o usuario
    // abre/troca de esquema com esta aba selecionada (ver
    // MainWindow#openSchema). Persistido junto com titulo/SQL/id (ver
    // SessionStore.Tab#schema) para que a aba "lembre" seu esquema entre
    // sessoes. Usado por MainWindow#onRun para conectar automaticamente no
    // esquema certo ANTES de rodar a instrucao desta aba, mesmo que o
    // esquema "atual" da conexao (compartilhado por todas as abas) esteja
    // apontando para outro lugar no momento.
    private String schema;

    private final SearchContext searchContext = new SearchContext();
    private JPanel findBar;
    private JDialog findPopup;
    private JTextField findField;
    private JTextField replaceField;
    private JCheckBox matchCaseBtn;
    private JCheckBox wholeWordBtn;
    private JCheckBox regexBtn;
    /**
     * Historico de "Localizar"/"Substituir" — pedido explicito do usuario
     * ("Algo que sinto falta em praticamente todos os editores"). Estatico
     * (compartilhado por TODAS as abas, nao por instancia): o usuario
     * espera o mesmo historico ao trocar de aba, igual buscadores de
     * editores de texto reais — nao persistido em disco (some ao fechar a
     * IDE), so em memoria durante a sessao.
     */
    private static final LinkedList<String> FIND_HISTORY = new LinkedList<>();
    private static final LinkedList<String> REPLACE_HISTORY = new LinkedList<>();
    private static final int MAX_HISTORY = 15;
    /** Quantas ocorrencias o painel de pre-visualizacao mostra no maximo — ver {@link #updatePreview}. */
    private static final int MAX_PREVIEW_ENTRIES = 10;
    private JTextArea previewArea;
    private JScrollPane previewScroll;
    private Timer previewDebounce;
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
     * Nome de exibicao da CONEXAO ativa desta aba (ex.: o nome dado pelo
     * usuario em {@code ConnectionEditDialog}), consultado sob demanda a
     * cada atualizacao do breadcrumb — mesma ideia de {@link #schemaSupplier}
     * (nunca cacheado, reflete sempre a conexao atual mesmo que o usuario
     * troque com a aba ja aberta). {@code null}-safe: quando nao informado
     * (construtor de 8 argumentos), o breadcrumb simplesmente comeca no
     * schema, sem prefixo de conexao.
     */
    private final Supplier<String> connectionNameSupplier;

    /**
     * Chamado quando o usuario da CTRL+Clique sobre um objeto de banco
     * reconhecido no editor (secao 8.3 do pedido "Navegacao Inteligente e
     * Interativa") — {@code kind} e "TABLE"/"VIEW"/"PROCEDURE"/"FUNCTION"/
     * "TRIGGER" (mesmos valores usados pela arvore de objetos, ver
     * {@code ObjectExplorerController.ObjNode#kind()}); {@code table} vem
     * preenchido so para TABLE/VIEW, {@code null} para os demais.
     * {@code ObjectExplorerController} decide o que fazer (abrir a tela de
     * propriedades, no caso).
     */
    @FunctionalInterface
    interface ObjectOpenHandler {
        void open(String kind, String name, TableInfo table);
    }

    private final ObjectOpenHandler onOpenObject;
    /** Guardado so pra {@link #buildEditorPopupMenu} conseguir oferecer "Executar esta instrucao" — mesmo Runnable de {@link #bindRunAndFormatShortcuts}/{@link #buildBreadcrumbBar}. */
    private final Runnable onRun;

    public SqlEditorPane(String tabId, SqlCompletionProviderRSyntax provider, Runnable onRun,
            Supplier<SqlFormatter> formatterSupplier, String fontFamily, Supplier<SchemaInfo> schemaSupplier,
            ObjectOpenHandler onOpenObject, Runnable onNavigateBack) {
        this(tabId, provider, onRun, formatterSupplier, fontFamily, schemaSupplier, onOpenObject, onNavigateBack,
                null, null, null);
    }

    /**
     * Igual ao construtor de 8 argumentos, com mais 2 callbacks NULL-SAFE
     * para a fileira de acoes rapidas do canto direito da barra de contexto
     * (ver {@link #buildQuickActionRow}): {@code onToggleExpand} (icone de
     * expandir/recolher o editor) e {@code onMoreOptions} (icone de "mais
     * opcoes", recebe o proprio botao como ancora para o popup). Qualquer um
     * pode ser {@code null} — o botao correspondente simplesmente nao e
     * criado. Mais {@code connectionNameSupplier} (tambem null-safe) para o
     * prefixo de conexao do breadcrumb — ver {@link #connectionNameSupplier}.
     * (Ate a revisao de UX que consolidou "Historico" numa aba unica da
     * sidebar, havia tambem um {@code onHistory} aqui — removido por ser um
     * duplicado exato do botao "Historico" da barra de ferramentas, ver
     * {@code MainWindow#addSaveButton}.)
     */
    public SqlEditorPane(String tabId, SqlCompletionProviderRSyntax provider, Runnable onRun,
            Supplier<SqlFormatter> formatterSupplier, String fontFamily, Supplier<SchemaInfo> schemaSupplier,
            ObjectOpenHandler onOpenObject, Runnable onNavigateBack,
            Runnable onToggleExpand, Consumer<JComponent> onMoreOptions,
            Supplier<String> connectionNameSupplier) {
        super(new BorderLayout());

        this.tabId = tabId;
        this.formatterSupplier = formatterSupplier;
        this.fontFamily = fontFamily;
        this.schemaSupplier = schemaSupplier;
        this.connectionNameSupplier = connectionNameSupplier;
        this.onOpenObject = onOpenObject;
        this.onRun = onRun;
        this.textArea = buildTextArea();
        // Ligado JA AQUI (antes de qualquer setText(sql) que o chamador venha
        // a fazer pra carregar o conteudo inicial da aba) — MainWindow chama
        // discardUndoHistory() logo depois de carregar o SQL salvo, pra esse
        // carregamento inicial nao entrar no historico de desfazer (ver
        // MainWindow#addQueryTab).
        this.undoManager = new EditorUndoManager(textArea);
        configureTextAreaAppearance(fontFamily);
        installAutocomplete(provider);
        bindRunAndFormatShortcuts(onRun);
        bindFindReplaceShortcuts();
        bindCaseShortcuts();
        bindUndoRedoShortcuts();
        bindZoomShortcuts();
        bindNavigationShortcuts(onNavigateBack);

        RTextScrollPane scroll = buildScrollPane();
        add(buildBreadcrumbBar(onRun, onToggleExpand, onMoreOptions), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        buildFindBar();
        installBreadcrumbSync(textArea);
    }

    private RSyntaxTextArea buildTextArea() {
        RSyntaxTextArea area = new RSyntaxTextArea(20, 80) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {
                EditorObjectHit hit = resolveObjectAt(viewToModel2D(e.getPoint()));
                return (hit != null) ? tooltipHtmlFor(hit) : null;
            }
        };
        javax.swing.ToolTipManager.sharedInstance().registerComponent(area);
        // setSyntaxEditingStyle continua chamado primeiro: e o nome de
        // estilo guardado na PROPRIA RSyntaxTextArea (nao no documento) que
        // o code folding usa pra achar o FoldParser certo (ver SqlFoldParser
        // em App.java) — so DEPOIS trocamos o TokenMaker do documento pelo
        // nosso, instanciado diretamente (nao mais via TokenMakerFactory,
        // que so cria por reflexao com construtor sem argumentos e nao
        // deixaria a instancia "conhecer" esta RSyntaxTextArea — ver
        // SqlHighlightTokenMaker#getTokenList para o motivo de precisar
        // disso: negritar aliases usados ANTES do FROM que os define).
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        ((RSyntaxDocument) area.getDocument()).setSyntaxStyle(new SqlHighlightTokenMaker(area));
        area.setCodeFoldingEnabled(true);
        area.setTabSize(2);
        area.setText("");
        return area;
    }

    private void configureTextAreaAppearance(String fontFamily) {
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
        // Cores do editor (fundo, realces) dependem do tema ativo no momento
        // em que a aba e criada — ver #applyEditorPalette. ANTES o fundo era
        // fixo em cinza claro (0xF6F7F9) MESMO com o app inteiro no tema
        // escuro: a aba de SQL virava uma caixa clara "fora do tema" (o
        // mesmo bug relatado na grade de resultados, ver GridTheme). Os
        // realces translucidos continuam na mesma familia de cor (verde da
        // marca), so a base (fundo/selecao) muda com o tema.
        applyEditorPalette();
        installCurrentStatementHighlight(textArea);
        installObjectHover(textArea);
        installReferenceHighlight(textArea);
        // Substitui o menu de contexto padrao do RSyntaxTextArea (que teria
        // seu PROPRIO "Desfazer"/"Refazer" ligado ao undo manager interno da
        // biblioteca) pelo nosso, ligado ao EditorUndoManager — sem isso,
        // clique direito e Ctrl+Z desfariam coisas diferentes.
        textArea.setComponentPopupMenu(buildEditorPopupMenu());
    }

    private void installAutocomplete(SqlCompletionProviderRSyntax provider) {
        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(200);
        // Mesmo com UMA unica sugestao, mostra o popup em vez de inserir
        // automaticamente: a insercao so ocorre quando o usuario escolhe
        // (clique, Enter ou Ctrl+Espaco), para nao atrapalhar a digitacao.
        ac.setAutoCompleteSingleChoices(false);
        ac.install(textArea);
    }

    /** Executa: Ctrl+Enter (preferido) e F5. Ctrl+Shift+F formata (beautifier). */
    private void bindRunAndFormatShortcuts(Runnable onRun) {
        textArea.getActionMap().put("run-sql", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onRun.run();
            }
        });
        textArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "run-sql");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run-sql");

        textArea.getInputMap().put(KeyStroke.getKeyStroke("control shift F"), "format-sql");
        textArea.getActionMap().put("format-sql", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                formatText();
            }
        });
    }

    /**
     * Ctrl+F / Ctrl+H abrem a MESMA barra (localizar + substituir ja
     * aparecem juntos, sem aba separada) — mas cada um foca o campo que o
     * usuario espera pelo atalho: Ctrl+F -> "Localizar", Ctrl+H ->
     * "Substituir" (convencao de Word/VS Code/etc.). Antes os dois focavam
     * sempre "Localizar", entao Ctrl+H nao se comportava diferente de Ctrl+F
     * — corrigido aqui.
     */
    private void bindFindReplaceShortcuts() {
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
    }

    /** Caixa: Ctrl+U / Ctrl+Shift+U -> MAIUSCULAS ; Ctrl+L / Ctrl+Shift+L -> minusculas. */
    private void bindCaseShortcuts() {
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
    }

    /**
     * Desfazer/Refazer: Ctrl+Z / Ctrl+Y (Ctrl+Shift+Z como alias comum de
     * "refazer", igual boa parte dos editores) passam a chamar o
     * EditorUndoManager PROPRIO (ver campo undoManager), nao o undo/redo
     * padrao do RSyntaxTextArea — o padrao da biblioteca desfaz LETRA POR
     * LETRA (uma edicao por chamada de insertString/remove do Document),
     * nunca o "grupo" que o usuario espera ao digitar uma palavra/frase de
     * uma vez (ver javadoc de EditorUndoManager). Registrado tanto no
     * InputMap/ActionMap quanto no KeyListener bruto abaixo, pelo MESMO
     * motivo do Ctrl+U/Ctrl+L (ver comentario logo abaixo).
     * <p>
     * Reforco: os bindings de InputMap/ActionMap acima as vezes NAO
     * disparavam (usuario relatou Ctrl+U/Ctrl+L sem nenhum efeito numa
     * selecao grande — e depois, Ctrl+Z "nao funcionando direito" e perdendo
     * digitacao). Causa provavel: o Swing notifica TODOS os KeyListener's
     * registrados no editor (inclusive o da biblioteca de autocomplete,
     * instalada via {@link #installAutocomplete}) ANTES de processar os key
     * bindings do InputMap — se qualquer um deles consumir o evento
     * (e.consume()) por algum motivo proprio, o binding de InputMap
     * simplesmente nunca chega a rodar, sem erro nenhum. Um KeyListener
     * proprio, registrado diretamente, sempre RODA (listeners nao impedem
     * uns aos outros de serem chamados so por consumir o evento) —
     * chamando as acoes daqui direto, os atalhos funcionam independente do
     * que mais estiver escutando teclas no editor. Ctrl+V/Ctrl+X tambem
     * entram aqui (e nao so no InputMap padrao) pra sempre passar por
     * {@link EditorUndoManager#runAsSingleEdit}, garantindo colar/recortar
     * como UMA operacao propria no historico (nunca misturada com digitacao
     * ao redor).
     */
    private void bindUndoRedoShortcuts() {
        textArea.getActionMap().put("nureal-undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performUndo();
            }
        });
        textArea.getActionMap().put("nureal-redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performRedo();
            }
        });
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "nureal-undo");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Y"), "nureal-redo");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control shift Z"), "nureal-redo");

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
                } else if (e.getKeyCode() == KeyEvent.VK_Z) {
                    if (e.isShiftDown()) {
                        performRedo();
                    } else {
                        performUndo();
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_Y) {
                    performRedo();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_V && !e.isShiftDown() && textArea.isEditable()) {
                    pasteFast();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_X && !e.isShiftDown() && textArea.isEditable()) {
                    undoManager.runAsSingleEdit(textArea::cut);
                    e.consume();
                }
            }
        });
    }

    /** Colar so fica lento a partir daqui (ver {@link #pasteFast}) — abaixo disso a diferenca nao e perceptivel. */
    private static final int LARGE_PASTE_THRESHOLD_CHARS = 50_000;

    /**
     * Cola o conteudo da area de transferencia — igual a {@code textArea.paste()},
     * mas rapido tambem para textos GRANDES (scripts SQL de dezenas de
     * milhares de caracteres, pedido explicito do usuario: "textos grandes
     * quando colo no terminal devem ser colados de forma rapida"). A
     * insercao em si ja e uma unica mutacao do Document (nao O(n) por
     * caractere); o gargalo e o CODE FOLDING (ver
     * {@code SqlFoldParser}) reprocessando o documento inteiro logo em
     * seguida — desligado temporariamente so quando o colado e grande o
     * suficiente pra isso importar, e religado (com um unico
     * reprocessamento, nao um por caractere) assim que a colagem termina.
     * Sempre uma unica operacao no historico de desfazer (ver
     * {@link EditorUndoManager#runAsSingleEdit}), como antes.
     */
    private void pasteFast() {
        String clipboardText = clipboardTextOrNull();
        boolean large = clipboardText != null && clipboardText.length() > LARGE_PASTE_THRESHOLD_CHARS;
        if (!large) {
            undoManager.runAsSingleEdit(textArea::paste);
            return;
        }
        boolean foldingWasEnabled = textArea.isCodeFoldingEnabled();
        textArea.setCodeFoldingEnabled(false);
        try {
            undoManager.runAsSingleEdit(textArea::paste);
        } finally {
            if (foldingWasEnabled) {
                textArea.setCodeFoldingEnabled(true);
            }
        }
    }

    /** Texto atual da area de transferencia do sistema, ou {@code null} se vazia/indisponivel/nao-texto. */
    private static String clipboardTextOrNull() {
        try {
            Transferable content = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (content != null && content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) content.getTransferData(DataFlavor.stringFlavor);
            }
        } catch (Exception ignore) {
            // Clipboard indisponivel/ocupado por outro processo no instante da
            // checagem — segue sem a otimizacao (paste normal ainda funciona).
        }
        return null;
    }

    /** Zoom: Ctrl + '=' / '+' / numpad+  aumenta; Ctrl + '-' diminui; Ctrl+0 reseta. */
    private void bindZoomShortcuts() {
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
    }

    /**
     * Navegacao (secao 8.6 do pedido "Navegacao Inteligente e Interativa"):
     * F12 = ir para definicao do objeto sob o CURSOR (mesmo destino do
     * CTRL+Clique da secao 8.3, so que sem precisar do mouse); ALT+Seta-
     * esquerda = voltar ao objeto anterior no historico mantido por quem
     * chama (ver onNavigateBack). CTRL+Hover ja funciona sem nada extra
     * aqui: o tooltip da secao 8.2 (getToolTipText, ver buildTextArea)
     * aparece em qualquer hover sobre um objeto reconhecido, com ou sem
     * CTRL pressionado.
     */
    private void bindNavigationShortcuts(Runnable onNavigateBack) {
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
    }

    private RTextScrollPane buildScrollPane() {
        RTextScrollPane scroll = new RTextScrollPane(textArea);
        this.scrollPane = scroll;
        scroll.setBorder(BorderFactory.createEmptyBorder());
        applyGutterPalette();
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
        return scroll;
    }

    /**
     * Fundo/cores de sintaxe do editor de texto propriamente dito — le
     * {@link FlatLaf#isLafDark()} NA HORA (nunca cacheado), entao chamar de
     * novo (ver {@link #refreshTheme}) sempre reflete o tema atual, mesmo
     * numa aba que ja estava aberta antes da troca de claro/escuro.
     * <p>
     * ANTES o fundo era fixo em 0xF6F7F9 (cinza claro) e a cor de texto
     * padrao vinha do esquema de sintaxe PADRAO do RSyntaxTextArea (afinado
     * para fundo claro: texto/palavras-chave escuros) — no tema escuro isso
     * dava texto quase preto sobre fundo tambem escurecido manualmente,
     * ilegivel. Em vez de recolorir token por token na mao (arriscado e
     * dificil de manter consistente), usa os temas PRONTOS que o proprio
     * RSyntaxTextArea distribui ({@code dark.xml}/{@code default.xml}) —
     * cobrem fundo, texto padrao, palavras-chave, strings, comentarios,
     * numeros etc. de uma vez, testados pela propria biblioteca.
     */
    private void applyEditorPalette() {
        boolean dark = FlatLaf.isLafDark();
        String resource = dark ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in != null) {
                Theme.load(in).apply(textArea);
            }
        } catch (IOException ex) {
            // Segue com o que a textArea ja tinha — os realces abaixo
            // continuam sendo aplicados de qualquer forma.
        }
        // O tema recem-carregado reseta o SyntaxScheme inteiro — reaplica o
        // NEGRITO de nomes de tabela/view/procedure (ver o comentario
        // original no construtor: so o FONT muda, a cor continua vindo do
        // tema, clara ou escura).
        SyntaxScheme scheme = textArea.getSyntaxScheme();
        scheme.setStyle(TokenTypes.DATA_TYPE, new Style(null, null, textArea.getFont().deriveFont(Font.BOLD)));
        if (dark) {
            // Tema ESCURO: paleta propria estilo VS Code Dark+ (pedido
            // explicito do usuario, com screenshots comparando os dois temas:
            // "precisa ficar legivel com cores boas que contrastem... estilo
            // vscode"). Duas tentativas anteriores nao serviram — uma paleta
            // desaturada demais (baixo contraste, "sumia") e depois um
            // experimento so preto-vira-branco (sem NENHUMA cor de destaque,
            // FROM/WHERE/numeros todos iguais) — esta usa os tons EXATOS do
            // VS Code Dark+ como base, agora com keyword/string/numero/booleano
            // SUBSTITUIDOS pela paleta semantica unica (ver
            // applySemanticSyntaxColors) — pedido do "Sistema Semantico de
            // Cores por Tipo de Dado".
            applySemanticSyntaxColors(scheme, true);
            textArea.setBackground(new Color(0x1E, 0x1E, 0x1E));
            textArea.setForeground(new Color(0xD4, 0xD4, 0xD4));
        } else {
            // ANTES o tema CLARO usava o default.xml do RSyntaxTextArea sem
            // nenhuma sobrescrita ("no tema light fica como esta, correto").
            // O Sistema Semantico de Cores por Tipo de Dado agora EXIGE
            // explicitamente que "o Syntax Highlight utilize exatamente a
            // mesma paleta" nos dois temas — pedido novo que substitui aquela
            // decisao antiga; sem isto, a mesma coluna/valor mudaria de cor so
            // por alternar claro/escuro, o oposto do que o sistema pede.
            applySemanticSyntaxColors(scheme, false);
            // Sem isto, voltar do escuro para o claro (Theme.load(default.xml)
            // sozinho NAO reseta de forma confiavel o fundo/texto PROPRIOS do
            // componente, so o SyntaxScheme) deixava o fundo/texto presos nos
            // tons escuros de antes — usuario relatou o editor "quebrado,
            // branco" ao voltar pro tema claro (na verdade era o INVERSO: o
            // texto continuava no cinza quase-branco do tema escuro, ilegivel
            // sobre o fundo branco que o tema claro tentava aplicar).
            // Explicito aqui, sempre, em vez de confiar que o tema recem-
            // carregado ja resolveu sozinho.
            textArea.setBackground(Color.WHITE);
            textArea.setForeground(Color.BLACK);
        }
        // Realces de fundo do editor. O verde da marca usado como SELECAO
        // (bloco solido cobrindo o texto selecionado inteiro) foi trocado —
        // pedido explicito do usuario ("esse verde como background... e
        // enjoativo e nao combina, tanto no tema dark quanto no light"): o
        // verde fica reservado para acoes/destaques pontuais (botao
        // primario, dot de status), nunca mais como bloco de fundo. Selecao
        // agora e um azul neutro discreto, mesma familia do editor estilo
        // VS Code (Dark+ usa #264F78 pra selecao; a variante clara usa o
        // mesmo tom de azul, so mais suave). Linha atual continua neutra
        // (branco/preto bem translucido) — e o UNICO realce que fica ligado
        // o tempo todo, precisa ser o mais discreto de todos.
        textArea.setCurrentLineHighlightColor(
                dark ? new Color(0xFF, 0xFF, 0xFF, 12) : new Color(0x00, 0x00, 0x00, 10));
        textArea.setSelectionColor(dark ? new Color(0x26, 0x4F, 0x78) : new Color(0xAD, 0xD6, 0xFF));
        textArea.setMarkAllHighlightColor(dark ? new Color(0x62, 0x3E, 0x00, 160) : new Color(0xFF, 0xE1, 0x64, 160));
    }

    /** Troca so a cor de um tipo de token, preservando fonte/negrito/italico que o tema ja definiu. */
    private static void setStyleColor(SyntaxScheme scheme, int tokenType, Color color) {
        Style existing = scheme.getStyle(tokenType);
        Style updated = (existing != null) ? (Style) existing.clone() : new Style();
        updated.foreground = color;
        scheme.setStyle(tokenType, updated);
    }

    /**
     * Paleta de sintaxe UNICA do editor — usa {@link GridTheme} (o mesmo
     * "Sistema Semantico de Cores por Tipo de Dado" da grade de resultados,
     * ver DESIGN_SYSTEM.md) para palavras-chave, literais de texto/numero/
     * booleano, e uma paleta PROPRIA (nao ligada a tipo de dado) so para
     * comentario/funcao/operador/variavel — elementos de SINTAXE, nao de
     * TIPO. {@code dark} escolhe qual variante de {@link GridTheme} esta
     * ativa no momento (o campo ja foi trocado por {@code GridTheme#applyPalette}
     * antes desta chamada, ver {@code MainWindow#toggleTheme}).
     * <p>
     * LIMITACAO CONHECIDA (documentada em DESIGN_SYSTEM.md): um literal de
     * string como {@code '2026-06-01'} NAO pode ser distinguido de
     * {@code 'João'} so pela sintaxe — os dois sao a MESMA producao lexica
     * (string entre aspas simples). Colori-los de forma diferente exigiria
     * saber contra qual COLUNA (e o tipo dela) aquele literal esta sendo
     * comparado — analise semantica bem alem de um syntax highlighter, fora
     * do escopo desta rodada. Todo literal de string usa a cor de TEXTO.
     */
    private static void applySemanticSyntaxColors(SyntaxScheme scheme, boolean dark) {
        Color comment = dark ? new Color(0x7F, 0x84, 0x8E) : new Color(0x8A, 0x91, 0x99);
        Color function = dark ? new Color(0xDC, 0xDC, 0xAA) : new Color(0x82, 0x77, 0x17);
        Color identifier = dark ? new Color(0xD4, 0xD4, 0xD4) : Color.BLACK;
        Color operator = identifier;
        Color variable = dark ? new Color(0x9C, 0xDC, 0xFE) : new Color(0x00, 0x60, 0xC0);

        setStyleColor(scheme, TokenTypes.RESERVED_WORD, GridTheme.COLOR_KEYWORD); // SELECT/FROM/WHERE...
        setStyleColor(scheme, TokenTypes.RESERVED_WORD_2, GridTheme.COLOR_KEYWORD);
        setStyleColor(scheme, TokenTypes.FUNCTION, function); // COUNT()/NOW()...
        setStyleColor(scheme, TokenTypes.IDENTIFIER, identifier); // texto comum/colunas
        setStyleColor(scheme, TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, GridTheme.COLOR_TEXTUAL); // 'valor'
        setStyleColor(scheme, TokenTypes.LITERAL_CHAR, GridTheme.COLOR_TEXTUAL);
        setStyleColor(scheme, TokenTypes.LITERAL_NUMBER_DECIMAL_INT, GridTheme.COLOR_INTEGER);
        setStyleColor(scheme, TokenTypes.LITERAL_NUMBER_FLOAT, GridTheme.COLOR_DECIMAL);
        setStyleColor(scheme, TokenTypes.LITERAL_NUMBER_HEXADECIMAL, GridTheme.COLOR_INTEGER);
        setStyleColor(scheme, TokenTypes.LITERAL_BOOLEAN, GridTheme.COLOR_BOOLEAN); // TRUE/FALSE (ver SqlHighlightTokenMaker)
        setStyleColor(scheme, TokenTypes.COMMENT_EOL, comment);
        setStyleColor(scheme, TokenTypes.COMMENT_MULTILINE, comment);
        setStyleColor(scheme, TokenTypes.COMMENT_DOCUMENTATION, comment);
        setStyleColor(scheme, TokenTypes.OPERATOR, operator);
        setStyleColor(scheme, TokenTypes.SEPARATOR, operator);
        setStyleColor(scheme, TokenTypes.VARIABLE, variable);
        // DATA_TYPE (nomes de tabela/view/procedure) fica de fora de proposito:
        // continua so em NEGRITO, cor null (herda a de IDENTIFIER acima) —
        // mesmo comportamento pedido originalmente (ver o comentario no
        // construtor) — "Objetos" e "Colunas" do sistema semantico sao
        // deliberadamente neutros/brancos, para nao competir com as
        // palavras-chave (ver GridTheme.COLOR_OBJECT_NAME/COLOR_COLUMN_NAME).
    }

    /** Gutter (numeros de linha) — mesma logica de {@link #applyEditorPalette}. */
    private void applyGutterPalette() {
        boolean dark = FlatLaf.isLafDark();
        scrollPane.getGutter().setBackground(dark ? new Color(0x1A, 0x1B, 0x1E) : new Color(0xEC, 0xEE, 0xF1));
        scrollPane.getGutter().setBorderColor(dark ? new Color(0x33, 0x36, 0x3A) : new Color(0xEB, 0xED, 0xEF));
    }

    /**
     * Chamado por {@code MainWindow#toggleTheme} em toda aba ja aberta,
     * assim que o usuario alterna claro/escuro — sem isto, so abas NOVAS
     * (criadas DEPOIS da troca) ficariam com a cor certa; as que ja estavam
     * na tela ficariam presas na cor de quando foram criadas (o fundo do
     * editor e o gutter sao definidos uma unica vez no construtor, nao a
     * cada pintura).
     */
    public void refreshTheme() {
        applyEditorPalette();
        applyGutterPalette();
        if (breadcrumbBar != null) {
            breadcrumbBar.setBackground(FlatLaf.isLafDark() ? new Color(0x1A, 0x1B, 0x1E) : new Color(0xEC, 0xEE, 0xF1));
        }
        if (breadcrumbLabel != null) {
            breadcrumbLabel.setForeground(breadcrumbForeground());
        }
        // findBar (barra de localizar/substituir) so fica dentro de uma
        // Window de verdade ENQUANTO o popup de busca esta aberto (ver
        // #getOrCreateFindPopup) — o resto do tempo seu ultimo parent e um
        // JDialog ja fechado, fora de Window.getWindows(), e portanto fora
        // do alcance do FlatLaf.updateUI() disparado por
        // MainWindow#toggleTheme. Mesma classe de bug ja corrigida para os
        // paineis de Conexoes/Historico/SQLs/Salvas (ver AnchoredPopup) —
        // sem isto, o popup de busca ficava preso no tema de quando a aba
        // foi criada ate a proxima vez que fosse reaberto.
        if (findBar != null) {
            javax.swing.SwingUtilities.updateComponentTreeUI(findBar);
        }
        repaint();
    }

    /**
     * Cor do destaque de fundo da instrucao atual — ver
     * {@link #installCurrentStatementHighlight}. Historico do ajuste: alpha
     * 16 (~6%) ficou "quase imperceptivel"; 42 (~16%) ficou escuro/pesado
     * demais, incomodando ao digitar (usuario relatou "sensacao ruim").
     * 24 (~9%) ainda ficou um pouco forte; 20 (~8%) e o ponto de equilibrio
     * pedido pelo usuario — visivel o suficiente pra indicar a instrucao
     * atual, sem incomodar durante a digitacao.
     */
    private static final Color CURRENT_STATEMENT_BG = new Color(0x64, 0x74, 0x8B, 20);

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
            int caret = textArea.getCaretPosition();
            int[] rawBounds = SqlStatementLocator.boundsAt(textArea.getText(), caret);
            // SqlStatementLocator.boundsAt so apara espacos das PONTAS do
            // texto encontrado — ele NAO verifica se o cursor de fato caiu
            // dentro do resultado. Numa linha em branco ENTRE duas
            // instrucoes, o cursor fica ANTES do inicio aparado (que e o
            // comeco da PROXIMA instrucao), entao sem essa checagem a linha
            // vazia contava como pertencendo a instrucao seguinte. Fora
            // desse caso (inclusive linhas em branco DENTRO de uma mesma
            // instrucao), o cursor sempre cai dentro do intervalo normal.
            int[] bounds = (caret >= rawBounds[0] && caret <= rawBounds[1]) ? rawBounds : new int[] {-1, -1};
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
        Highlighter.HighlightPainter underline = new UnderlineHighlightPainter();
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
                // Triplo-clique (nao mais duplo): seleciona a INSTRUCAO SQL
                // inteira sob o cursor (ate o ";" anterior/seguinte, ver
                // SqlStatementLocator — mesma logica ja usada pelo destaque de
                // fundo da instrucao atual) — pedido explicito do usuario de
                // poder selecionar uma instrucao inteira com um clique extra,
                // pra executar so ela (ver tambem "Executar esta instrucao"
                // no menu de contexto, buildEditorPopupMenu). Duplo-clique
                // (getClickCount()==2) foi DELIBERADAMENTE deixado de fora
                // daqui: o padrao do Swing/RSyntaxTextArea (selecionar so a
                // PALAVRA sob o cursor) ja roda sozinho no mousePressed antes
                // deste mouseClicked — inicialmente esta selecao de instrucao
                // tinha sido colocada no duplo-clique, mas isso SOBRESCREVIA
                // a selecao de palavra que o usuario esperava (relatado com
                // exemplo: "clico duas vezes pra selecionar uma palavra e
                // acaba selecionando a instrucao inteira"); triplo-clique e a
                // convencao mais comum pra "selecionar o bloco/linha inteira"
                // em editores de texto, sem tirar o duplo-clique do lugar.
                if (e.getClickCount() == 3 && !javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    int offset = textArea.viewToModel2D(e.getPoint());
                    int[] bounds = SqlStatementLocator.boundsAt(textArea.getText(), offset);
                    if (bounds[1] > bounds[0]) {
                        textArea.select(bounds[0], bounds[1]);
                    }
                    return;
                }
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
     * Cor do destaque de referencias (tabela + alias) — ver
     * {@link #installReferenceHighlight}. Le {@link FlatLaf#isLafDark()} a
     * cada pintura (nao guarda uma copia): antes era um {@code Color}
     * {@code static final} unico (alpha 55/255, ~21%) pros dois temas — no
     * tema escuro isso ficava "pouco visivel" (relato do usuario, com
     * captura de tela mostrando o destaque quase imperceptivel em cima de
     * "operation"/"o"). Agora e um alpha bem mais forte (~130-150/255),
     * ainda na mesma familia dourado/ambar do "mark all" (ver
     * {@code MARK_ALL_HIGHLIGHT_COLOR} em {@link #applyEditorPalette}) pra
     * continuar lendo como "isto e uma referencia", nao um erro/aviso.
     */
    private static Color referenceHighlightColor() {
        return FlatLaf.isLafDark() ? new Color(0xD1, 0xA3, 0x3B, 150) : new Color(0xB8, 0x86, 0x0B, 110);
    }

    /**
     * Pinta o destaque de referencias com {@link #referenceHighlightColor()}
     * lido NA HORA de cada repaint (mesma tecnica de {@link UnderlineHighlightPainter}
     * logo abaixo) — sem isto, a cor ficaria congelada no valor de quando o
     * highlighter foi instalado (uma unica vez, no construtor da aba),
     * exatamente o bug que motivou esta correcao: alternar claro/escuro numa
     * aba ja aberta nao mudava mais nada aqui, so em abas novas.
     */
    private static final class ReferenceHighlightPainter implements Highlighter.HighlightPainter {
        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r0 = c.modelToView2D(p0).getBounds();
                Rectangle r1 = c.modelToView2D(p1).getBounds();
                g.setColor(referenceHighlightColor());
                if (r0.y == r1.y) {
                    g.fillRect(r0.x, r0.y, Math.max(1, r1.x - r0.x), r0.height);
                } else {
                    // Nome de tabela/alias nunca deveria quebrar linha, mas
                    // por seguranca pinta cada linha separada em vez de um
                    // retangulo so, que ficaria "torto" atravessando linhas.
                    Rectangle alloc = bounds.getBounds();
                    g.fillRect(r0.x, r0.y, alloc.x + alloc.width - r0.x, r0.height);
                    for (int y = r0.y + r0.height; y < r1.y; y += r0.height) {
                        g.fillRect(alloc.x, y, alloc.width, r0.height);
                    }
                    g.fillRect(alloc.x, r1.y, Math.max(1, r1.x - alloc.x), r1.height);
                }
            } catch (BadLocationException ignored) {
                // offset invalido (documento mudou na hora): so nao pinta desta vez
            }
        }
    }

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
        Highlighter.HighlightPainter painter = new ReferenceHighlightPainter();
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
    private static Color breadcrumbForeground() {
        return FlatLaf.isLafDark() ? new Color(0x9A, 0xA3, 0xAF) : new Color(0x60, 0x6B, 0x7A);
    }

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
    private JComponent buildBreadcrumbBar(Runnable onRun, Runnable onToggleExpand,
            Consumer<JComponent> onMoreOptions) {
        breadcrumbLabel = new JLabel(" ");
        breadcrumbLabel.setForeground(breadcrumbForeground());
        breadcrumbLabel.setFont(breadcrumbLabel.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        bar.setBackground(FlatLaf.isLafDark() ? new Color(0x1A, 0x1B, 0x1E) : new Color(0xEC, 0xEE, 0xF1));
        bar.add(breadcrumbLabel, BorderLayout.WEST);
        bar.add(buildQuickActionRow(onRun, onToggleExpand, onMoreOptions), BorderLayout.EAST);
        this.breadcrumbBar = bar;
        return bar;
    }

    /**
     * Fileira de icones de acao rapida no canto direito da barra de contexto
     * do editor (pedido explicito do usuario, visto nos prints de
     * referencia): Executar, Expandir e Mais opcoes — as MESMAS acoes ja
     * acessiveis por atalho de teclado/barra de ferramentas/menu de contexto
     * da aba, so mais a mao sem precisar sair do editor. Callbacks
     * {@code null}-safe: nenhum botao e criado para o callback que vier
     * {@code null} (o construtor de 8 argumentos passa todos {@code null},
     * entao quem nao precisar da fileira completa nao ganha nenhum botao
     * extra alem do Executar, que sempre existe). Nao tem mais um botao de
     * Historico aqui (revisao de UX: era duplicado exato do botao de mesmo
     * nome na barra de ferramentas — ver {@code MainWindow#addSaveButton}).
     */
    private JComponent buildQuickActionRow(Runnable onRun, Runnable onToggleExpand,
            Consumer<JComponent> onMoreOptions) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        row.setOpaque(false);

        JButton runButton = new JButton(Icons.get(IconType.RUN, 13, IconTheme.GREEN));
        runButton.setToolTipText("Executar (Ctrl+Enter ou F5)");
        runButton.addActionListener(e -> onRun.run());
        row.add(runButton);

        if (onToggleExpand != null) {
            JButton expandButton = new JButton();
            Buttons.bindThemedIcon(expandButton, IconType.EXPAND, 13, () -> GridTheme.MUTED_TEXT);
            expandButton.setToolTipText("Expandir/recolher editor (oculta paineis laterais)");
            expandButton.addActionListener(e -> onToggleExpand.run());
            row.add(expandButton);
        }

        if (onMoreOptions != null) {
            JButton moreButton = new JButton();
            Buttons.bindThemedIcon(moreButton, IconType.MORE, 13, () -> GridTheme.MUTED_TEXT);
            moreButton.setToolTipText("Mais opcoes desta aba");
            moreButton.addActionListener(e -> onMoreOptions.accept(moreButton));
            row.add(moreButton);
        }

        for (java.awt.Component c : row.getComponents()) {
            if (c instanceof JButton btn) {
                Buttons.styleIconButton(btn);
            }
        }
        return row;
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
        // Pedido explicito do usuario ("nao esta mostrando o breadcrumb
        // inteiro, conexao, schema, tabela, alias, colunas etc"): o prefixo
        // "Conexao > Schema" agora aparece SEMPRE que disponivel, em
        // qualquer um dos retornos deste metodo — antes so o schema entrava,
        // e so em alguns dos casos (cursor sobre tabela/coluna reconhecida),
        // nunca sobre uma palavra-chave/espaco em branco.
        String prefix = connectionAndSchemaPrefix(schemaName);
        String full = textArea.getText();
        int[] span = wordSpanAt(full, offset);
        if (span == null) {
            return prefix.isEmpty() ? null : prefix.substring(0, prefix.length() - 3); // tira o " > " final solto
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
                        return prefix + "Procedure > " + p;
                    }
                }
                for (String f : schema.functions()) {
                    if (f.equalsIgnoreCase(rawWord)) {
                        return prefix + "Function > " + f;
                    }
                }
                for (String tg : schema.triggers()) {
                    if (tg.equalsIgnoreCase(rawWord)) {
                        return prefix + "Trigger > " + tg;
                    }
                }
            }
            if (SqlHighlightTokenMaker.KEYWORDS.contains(upperWord) || SqlHighlightTokenMaker.FUNCTIONS.contains(upperWord)) {
                // palavra-chave/funcao SQL: sem contexto de objeto, mas o
                // prefixo conexao/schema continua util (o usuario sempre sabe
                // ONDE esta trabalhando, mesmo parado num SELECT/FROM).
                return prefix.isEmpty() ? null : prefix.substring(0, prefix.length() - 3);
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
        StringBuilder sb = new StringBuilder(prefix);
        sb.append((tableName != null) ? tableName : qualifier);
        // Alias diferente do nome real da tabela ("o" para "operation_order",
        // por exemplo) — mostra os DOIS, pedido explicito do usuario
        // ("...tabela, alias..."); antes o alias era descartado assim que
        // resolvia pra tabela real, sem nenhum jeito de saber qual alias
        // estava em uso so olhando o breadcrumb.
        if (tableName != null && qualifier != null && !qualifier.equalsIgnoreCase(tableName)) {
            sb.append(" (").append(qualifier).append(")");
        }
        if (column != null) {
            sb.append(" > ").append(column);
        }
        return sb.toString();
    }

    /** {@code "Conexao > Schema > "} (qualquer um dos dois ausente e omitido) — prefixo comum a qualquer retorno de {@link #computeBreadcrumb}. */
    private String connectionAndSchemaPrefix(String schemaName) {
        String connectionName = (connectionNameSupplier != null) ? connectionNameSupplier.get() : null;
        StringBuilder sb = new StringBuilder();
        if (connectionName != null && !connectionName.isBlank()) {
            sb.append(connectionName).append(" > ");
        }
        if (schemaName != null && !schemaName.isBlank()) {
            sb.append(schemaName).append(" > ");
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

    /**
     * Sublinhado discreto sob um trecho de texto — ver {@link #installObjectHover}.
     * SEM campo de cor proprio de proposito: le {@link GridTheme#HEADER_FOREGROUND}
     * DIRETO a cada pintura (nao guarda uma copia no construtor) — antes a
     * cor vinha de uma constante {@code static final} da classe (congelada no
     * carregamento, sempre o tom do tema CLARO), entao o sublinhado ficava
     * escuro-sobre-escuro (quase invisivel) no tema escuro, mesma familia de
     * bug ja vista e corrigida em outros lugares do app (cor explicita que
     * nao acompanha {@code GridTheme#applyPalette}).
     */
    private static final class UnderlineHighlightPainter implements Highlighter.HighlightPainter {

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r0 = c.modelToView2D(p0).getBounds();
                Rectangle r1 = c.modelToView2D(p1).getBounds();
                g.setColor(GridTheme.HEADER_FOREGROUND);
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

    /**
     * Roda SO a instrucao sob o cursor (ate o ";" anterior/seguinte, ver
     * {@link SqlStatementLocator}) — mesma acao de "Executar esta instrucao"
     * no menu de contexto ({@link #buildEditorPopupMenu}), extraida aqui pra
     * o dropdown "Executar ▾" da barra de ferramentas ({@code
     * MainWindow#addRunFormatExplainButtons}) poder chamar sem duplicar a
     * logica. Seleciona a instrucao primeiro e so entao chama {@link #onRun}
     * — mesmo caminho do botao "Executar"/Ctrl+Enter, sem logica de execucao
     * separada.
     */
    public void runStatementUnderCaret() {
        int[] bounds = SqlStatementLocator.boundsAt(textArea.getText(), textArea.getCaretPosition());
        if (bounds[1] > bounds[0]) {
            textArea.select(bounds[0], bounds[1]);
        }
        onRun.run();
    }

    /** Id da query salva a que esta aba esta ligada, ou {@code null} se nenhuma. */
    public String getSavedQueryId() {
        return savedQueryId;
    }

    public void setSavedQueryId(String savedQueryId) {
        this.savedQueryId = savedQueryId;
    }

    /** Esquema a que esta aba pertence, ou {@code null} se ainda nao definido. */
    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
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

        // Formatar e uma OPERACAO PROPRIA no historico de desfazer, nunca
        // misturada com digitacao antes/depois (pedido explicito do
        // usuario) — ver EditorUndoManager#runAsSingleEdit.
        undoManager.runAsSingleEdit(() -> {
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
        });
    }

    // ---------- Localizar / Substituir ----------

    private JComponent buildFindBar() {
        findField = new JTextField(22);
        replaceField = new JTextField(22);
        findStatus = new JLabel();
        Typography.tertiary(findStatus);

        // Checkboxes com rotulo por extenso (nao mais "Aa"/"W") — pedido
        // explicito do usuario: os botoes crípticos exigiam que o usuario
        // decorasse o que cada abreviacao significava, aumentando a carga
        // cognitiva. "Expressao regular" e novo: o SearchContext ja aceitava
        // regex (SearchEngine da RSyntaxTextArea), so nao havia opcao na UI
        // pra ligar — ver #configureSearch.
        matchCaseBtn = new JCheckBox("Diferenciar maiusculas/minusculas");
        wholeWordBtn = new JCheckBox("Palavra inteira");
        regexBtn = new JCheckBox("Expressao regular");
        for (JCheckBox cb : new JCheckBox[] { matchCaseBtn, wholeWordBtn, regexBtn }) {
            cb.setOpaque(false);
        }

        // Barra de localizar e construida UMA VEZ e so escondida/mostrada
        // (nao recriada) — os 3 icones abaixo precisam de Buttons.bindThemedIcon
        // (nao Icons.get(..., iconColor) resolvido uma unica vez), senao
        // ficam congelados no tema em que a aba foi aberta (mesmo bug
        // sistemico corrigido no resto do app, ver Buttons#bindThemedIcon).
        JButton prev = new JButton();
        Buttons.bindThemedIcon(prev, IconType.CHEVRON_LEFT, 12, () -> GridTheme.MUTED_TEXT);
        prev.setToolTipText("Anterior (Shift+Enter)");
        prev.addActionListener(e -> findPrevious());
        JButton next = new JButton();
        Buttons.bindThemedIcon(next, IconType.CHEVRON_RIGHT, 12, () -> GridTheme.MUTED_TEXT);
        next.setToolTipText("Proximo (Enter)");
        next.addActionListener(e -> findNext());
        JButton findHistoryBtn = new JButton();
        Buttons.bindThemedIcon(findHistoryBtn, IconType.HISTORY, 12, () -> GridTheme.MUTED_TEXT);
        findHistoryBtn.setToolTipText("Historico de busca");
        findHistoryBtn.addActionListener(e -> showHistoryMenu(findHistoryBtn, findField, FIND_HISTORY, this::findNext));
        JButton replaceOne = new JButton("Substituir");
        replaceOne.addActionListener(e -> replaceOne());
        JButton replaceAll = new JButton("Substituir tudo");
        replaceAll.addActionListener(e -> replaceAll());
        JButton replaceHistoryBtn = new JButton();
        Buttons.bindThemedIcon(replaceHistoryBtn, IconType.HISTORY, 12, () -> GridTheme.MUTED_TEXT);
        replaceHistoryBtn.setToolTipText("Historico de substituicao");
        replaceHistoryBtn.addActionListener(e -> showHistoryMenu(replaceHistoryBtn, replaceField, REPLACE_HISTORY, null));
        JButton close = new JButton();
        Buttons.bindThemedIcon(close, IconType.CLOSE, 12, () -> GridTheme.MUTED_TEXT);
        close.setToolTipText("Fechar (Esc)");
        close.addActionListener(e -> hideFindBar());

        findField.addActionListener(e -> findNext());
        replaceField.addActionListener(e -> replaceOne());
        bindKey(findField, "shift ENTER", this::findPrevious);
        bindKey(findField, "ESCAPE", this::hideFindBar);
        bindKey(replaceField, "ESCAPE", this::hideFindBar);

        // Mesmo padrao do resto do app (ver Buttons#styleIconButton) — antes
        // eram botoes PADRAO do Swing, unica barra da janela sem nenhum
        // estilo aplicado. Icone-so (prev/next/close) fica no estilo de
        // botao-so-de-icone (mesma linguagem da barra de ferramentas); os
        // dois com texto (Substituir/Substituir tudo) ficam "roundRect" (acao
        // secundaria, igual qualquer outro botao de texto do app).
        for (JButton btn : new JButton[] { prev, next, close, findHistoryBtn, replaceHistoryBtn }) {
            Buttons.styleIconButton(btn);
        }
        for (JButton btn : new JButton[] { replaceOne, replaceAll }) {
            Buttons.styleSecondary(btn);
        }

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.add(new JLabel("Localizar:"));
        row1.add(findField);
        row1.add(findHistoryBtn);
        row1.add(prev);
        row1.add(next);
        row1.add(findStatus);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.add(new JLabel("Substituir:"));
        row2.add(replaceField);
        row2.add(replaceHistoryBtn);
        row2.add(replaceOne);
        row2.add(replaceAll);
        row2.add(close);

        // Linha propria pras opcoes (nao mais espremidas ao lado do campo de
        // busca) — cada uma com rotulo por extenso, ver comentario acima.
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        row3.add(matchCaseBtn);
        row3.add(wholeWordBtn);
        row3.add(regexBtn);

        JComponent previewRow = buildPreviewPanel();

        // Pre-visualizacao ATUALIZADA a cada tecla/opcao alterada (nao so ao
        // clicar em "Substituir") — pedido explicito do usuario ("o maior
        // diferencial"): ver exatamente o que vai mudar (util em qualquer
        // modo, mas critico com regex e grupos de captura "$1", onde um
        // engano no padrao pode destruir SQL sem o usuario perceber antes
        // de rodar). Debounce de 150ms: reprocessar o documento inteiro a
        // CADA tecla digitada seria desperdicio numa aba grande.
        previewDebounce = new Timer(150, e -> updatePreview());
        previewDebounce.setRepeats(false);
        DocumentListener liveUpdate = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                previewDebounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                previewDebounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                previewDebounce.restart();
            }
        };
        findField.getDocument().addDocumentListener(liveUpdate);
        replaceField.getDocument().addDocumentListener(liveUpdate);
        for (JCheckBox cb : new JCheckBox[] { matchCaseBtn, wholeWordBtn, regexBtn }) {
            cb.addActionListener(e -> updatePreview());
        }

        findBar = new JPanel();
        findBar.setLayout(new BoxLayout(findBar, BoxLayout.Y_AXIS));
        findBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        findBar.add(row1);
        findBar.add(row2);
        findBar.add(row3);
        findBar.add(previewRow);
        return findBar;
    }

    /** Painel "antes/depois" — ver {@link #updatePreview}. Comeca vazio/escondido (nada digitado ainda). */
    private JComponent buildPreviewPanel() {
        previewArea = new JTextArea(6, 40);
        previewArea.setEditable(false);
        previewArea.setLineWrap(false);
        previewArea.setFont(textArea.getFont());
        previewArea.setTabSize(2);
        previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, GridTheme.HEADER_BORDER),
                BorderFactory.createEmptyBorder(4, 0, 0, 0)));
        previewScroll.setVisible(false);
        return previewScroll;
    }

    /**
     * Recalcula a pre-visualizacao "antes → depois" das primeiras
     * {@link #MAX_PREVIEW_ENTRIES} ocorrencias, SEM tocar no documento real
     * (ver {@link #computeReplacementPreview}) — so aparece quando ha
     * "Localizar" preenchido; unico jeito de saber o que seria alterado
     * antes de clicar em "Substituir"/"Substituir tudo" de verdade.
     */
    private void updatePreview() {
        String findText = findField.getText();
        if (findText == null || findText.isEmpty()) {
            previewScroll.setVisible(false);
            findBar.revalidate();
            packFindPopup();
            return;
        }
        PreviewResult preview = computeReplacementPreview(findText, replaceField.getText());
        if (preview == null) {
            previewArea.setText("Expressao regular invalida.");
        } else if (preview.entries.isEmpty()) {
            previewArea.setText("Nenhuma ocorrencia encontrada.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (PreviewEntry entry : preview.entries) {
                sb.append(entry.line).append(": ").append(entry.before).append('\n');
                sb.append("      -> ").append(entry.after).append('\n');
            }
            if (preview.totalCount > preview.entries.size()) {
                sb.append("\n... e mais ").append(preview.totalCount - preview.entries.size()).append(" ocorrencia(s)");
            }
            previewArea.setText(sb.toString());
            previewArea.setCaretPosition(0);
        }
        previewScroll.setVisible(true);
        findBar.revalidate();
        packFindPopup();
    }

    /** Dialogo redimensiona pra caber a pre-visualizacao (que so aparece/some, nunca cresce sem limite — ver {@link #buildPreviewPanel}). */
    private void packFindPopup() {
        if (findPopup != null && findPopup.isShowing()) {
            findPopup.pack();
        }
    }

    private record PreviewEntry(int line, String before, String after) {
    }

    private record PreviewResult(List<PreviewEntry> entries, int totalCount) {
    }

    /**
     * Reimplementa o casamento (sem usar {@link SearchEngine}, que so opera
     * MUTANDO o documento de verdade — inaceitavel pra uma pre-visualizacao
     * que roda a cada tecla) direto sobre o texto atual, respeitando as
     * MESMAS opcoes da barra (maiusculas/minusculas, palavra inteira,
     * regex — ver {@link #configureSearch}). {@code null} = regex invalida
     * (usuario ainda digitando o padrao).
     */
    private PreviewResult computeReplacementPreview(String findText, String replaceText) {
        Pattern pattern;
        try {
            pattern = buildPreviewPattern(findText);
        } catch (PatternSyntaxException ex) {
            return null;
        }
        String fullText = textArea.getText();
        Matcher matcher = pattern.matcher(fullText);
        List<PreviewEntry> entries = new ArrayList<>();
        int total = 0;
        int searchFrom = 0;
        while (searchFrom <= fullText.length() && matcher.find(searchFrom)) {
            total++;
            if (entries.size() < MAX_PREVIEW_ENTRIES) {
                entries.add(buildPreviewEntry(fullText, matcher, replaceText));
            }
            searchFrom = (matcher.end() == matcher.start()) ? matcher.end() + 1 : matcher.end();
        }
        return new PreviewResult(entries, total);
    }

    private Pattern buildPreviewPattern(String findText) {
        int flags = matchCaseBtn.isSelected() ? 0 : Pattern.CASE_INSENSITIVE;
        if (regexBtn.isSelected()) {
            return Pattern.compile(findText, flags);
        }
        String quoted = Pattern.quote(findText);
        String body = wholeWordBtn.isSelected() ? ("\\b" + quoted + "\\b") : quoted;
        return Pattern.compile(body, flags);
    }

    private PreviewEntry buildPreviewEntry(String fullText, Matcher matcher, String replaceText) {
        int lineStart = fullText.lastIndexOf('\n', matcher.start() - 1) + 1;
        int lineEnd = fullText.indexOf('\n', matcher.end());
        if (lineEnd < 0) {
            lineEnd = fullText.length();
        }
        String lineText = fullText.substring(lineStart, lineEnd);
        int lineNumber = 1;
        for (int i = 0; i < lineStart; i++) {
            if (fullText.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        int matchStartInLine = matcher.start() - lineStart;
        int matchEndInLine = matcher.end() - lineStart;
        String replacement = regexBtn.isSelected() ? applyBackreferences(matcher, replaceText) : replaceText;
        String afterLine = lineText.substring(0, matchStartInLine) + replacement + lineText.substring(matchEndInLine);
        return new PreviewEntry(lineNumber, lineText.strip(), afterLine.strip());
    }

    /** Substitui {@code $1}, {@code $2}... por {@code matcher.group(n)} — mesma sintaxe de grupo do modo regex. */
    private static String applyBackreferences(Matcher matcher, String replacement) {
        if (replacement == null || replacement.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < replacement.length(); i++) {
            char c = replacement.charAt(i);
            if (c == '\\' && i + 1 < replacement.length()) {
                out.append(replacement.charAt(++i));
            } else if (c == '$' && i + 1 < replacement.length() && Character.isDigit(replacement.charAt(i + 1))) {
                int j = i + 1;
                while (j < replacement.length() && Character.isDigit(replacement.charAt(j))) {
                    j++;
                }
                int group = Integer.parseInt(replacement.substring(i + 1, j));
                String value = (group <= matcher.groupCount()) ? matcher.group(group) : null;
                out.append(value != null ? value : "");
                i = j - 1;
            } else {
                out.append(c);
            }
        }
        return out.toString();
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
     * Entrada publica pro icone de busca da toolbar principal (ver
     * {@code MainWindow#buildSearchIconButton}): abre a MESMA barra de
     * localizar/substituir de sempre (Ctrl+F), com TODAS as opcoes (buscar,
     * substituir, proxima/anterior ocorrencia) — pedido explicito do
     * usuario ("a barra de busca do sql poderia ser icone que abre popup
     * ou algo assim com todas as opcoes de busca ou substituicao"). Antes
     * disto a toolbar tinha seu PROPRIO campo de texto (so buscar, sem
     * substituir) que so encaminhava pra ca — o icone remove esse campo
     * fixo (~200px sempre reservados na barra) e abre o find/replace
     * completo direto, sem duplicar nenhuma logica de busca nova.
     */
    /**
     * Alterna (nao so abre): clicar de novo no MESMO icone com a barra ja
     * aberta agora FECHA ela, em vez de nao fazer nada (so refocar o campo
     * "Localizar") — pedido explicito do usuario, mesmo comportamento de
     * "clicar de novo fecha" que os outros popups ancorados da toolbar ja
     * tem (Objetos/Historico/SQLs/Salvas, ver
     * {@code MainWindow.AnchoredPopup#toggle}).
     */
    void openFindReplace() {
        if (findPopup != null && findPopup.isShowing()) {
            hideFindBar();
            return;
        }
        showFindBar(false);
    }

    /**
     * Mostra a barra de localizar/substituir como um popup flutuante ancorado
     * no canto superior direito do editor (mesmo padrao visual dos outros
     * popups da toolbar — Conexoes/Historico/SQLs/Salvas, ver
     * {@code MainWindow.AnchoredPopup}) em vez da antiga barra fixa na parte
     * de baixo da aba — pedido explicito do usuario ("poderia abrir um
     * popup assim como as outras opcoes"). As duas linhas (localizar e
     * substituir) ja aparecem juntas — nao ha um "modo" separado.
     *
     * @param focusReplace {@code true} quando veio de Ctrl+H (foca "Substituir");
     *                     {@code false} quando veio de Ctrl+F (foca "Localizar").
     */
    private void showFindBar(boolean focusReplace) {
        String sel = textArea.getSelectedText();
        if (sel != null && !sel.isEmpty() && !sel.contains("\n")) {
            findField.setText(sel);
        }
        JDialog popup = getOrCreateFindPopup();
        if (!popup.isShowing()) {
            positionFindPopup(popup);
            popup.setVisible(true);
        }
        JTextField target = focusReplace ? replaceField : findField;
        target.requestFocusInWindow();
        target.selectAll();
        // Recalcula na hora (nao so via debounce do DocumentListener): o
        // documento pode ter mudado enquanto o popup estava fechado, e se
        // "sel" acima estava vazio o campo nem dispara o listener.
        updatePreview();
    }

    private void hideFindBar() {
        if (findPopup != null) {
            findPopup.setVisible(false);
        }
        clearMarks();
        textArea.requestFocusInWindow();
    }

    private JDialog getOrCreateFindPopup() {
        if (findPopup != null) {
            return findPopup;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = owner instanceof java.awt.Frame ? new JDialog((java.awt.Frame) owner)
                : owner instanceof java.awt.Dialog ? new JDialog((java.awt.Dialog) owner) : new JDialog();
        dialog.setUndecorated(true);
        dialog.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
        findBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GridTheme.HEADER_BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        dialog.setContentPane(findBar);
        dialog.pack();
        dialog.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                hideFindBar();
            }
        });
        findPopup = dialog;
        return dialog;
    }

    private void positionFindPopup(JDialog popup) {
        if (!this.isShowing()) {
            return;
        }
        java.awt.Point loc = this.getLocationOnScreen();
        int x = loc.x + Math.max(0, this.getWidth() - popup.getPreferredSize().width - 16);
        int y = loc.y + 8;
        popup.setLocation(x, y);
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
        searchContext.setRegularExpression(regexBtn.isSelected());
        searchContext.setSearchForward(forward);
        searchContext.setMarkAll(true);
        rememberHistory(FIND_HISTORY, findField.getText());
        rememberHistory(REPLACE_HISTORY, replaceField.getText());
    }

    private static void rememberHistory(LinkedList<String> history, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        history.remove(value);
        history.addFirst(value);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    /** Menu de historico ancorado no botao de relogio ao lado do campo (ver {@link #buildFindBar}). */
    private void showHistoryMenu(JComponent anchor, JTextField field, List<String> history, Runnable onPick) {
        JPopupMenu menu = new JPopupMenu();
        if (history.isEmpty()) {
            JMenuItem empty = new JMenuItem("Sem historico nesta sessao");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (String entry : history) {
                JMenuItem item = new JMenuItem(entry);
                item.addActionListener(e -> {
                    field.setText(entry);
                    field.requestFocusInWindow();
                    if (onPick != null) {
                        onPick.run();
                    }
                });
                menu.add(item);
            }
        }
        menu.show(anchor, 0, anchor.getHeight());
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
        // Substituir e uma OPERACAO PROPRIA no historico, nunca misturada
        // com digitacao ao redor — ver EditorUndoManager#runAsSingleEdit.
        SearchResult[] resultHolder = { null };
        undoManager.runAsSingleEdit(() -> {
            SearchResult result = SearchEngine.replace(textArea, searchContext);
            if (!result.wasFound()) {
                textArea.setCaretPosition(0);
                result = SearchEngine.replace(textArea, searchContext);
            }
            resultHolder[0] = result;
        });
        updateStatus(resultHolder[0]);
    }

    private void replaceAll() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(true);
        SearchResult[] resultHolder = { null };
        undoManager.runAsSingleEdit(() -> resultHolder[0] = SearchEngine.replaceAll(textArea, searchContext));
        findStatus.setText(resultHolder[0].getCount() + " substituicao(oes)");
    }

    /**
     * "3 / 18 ocorrencias" em vez de so "18 ocorrencia(s)" — pedido explicito
     * do usuario ("Enquanto navega" mostrar a posicao atual, nao so o
     * total). O indice ATUAL vem das marcacoes de "marcar todos" que o
     * proprio {@link SearchEngine#find}/{@code #replace} ja deixa no
     * highlighter (ver {@link RTextAreaHighlighter#getMarkAllHighlightRanges}) —
     * localiza qual marcacao comeca onde a selecao atual comecou.
     */
    private void updateStatus(SearchResult result) {
        int marked = result.getMarkedCount();
        if (!result.wasFound() && marked == 0) {
            findStatus.setText("Nenhum resultado");
            return;
        }
        int current = currentOccurrenceIndex();
        String plural = marked == 1 ? "ocorrencia" : "ocorrencias";
        findStatus.setText(current > 0 ? (current + " / " + marked + " " + plural) : (marked + " " + plural));
    }

    private int currentOccurrenceIndex() {
        if (!(textArea.getHighlighter() instanceof org.fife.ui.rtextarea.RTextAreaHighlighter highlighter)) {
            return 0;
        }
        List<org.fife.ui.rsyntaxtextarea.DocumentRange> ranges =
                new ArrayList<>(highlighter.getMarkAllHighlightRanges());
        java.util.Collections.sort(ranges);
        int selStart = textArea.getSelectionStart();
        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.get(i).getStartOffset() == selStart) {
                return i + 1;
            }
        }
        return 0;
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
     * Fonte monoespacada PADRAO da IDE, no tamanho dado — mesma deteccao
     * (JetBrains Mono/Fira Code/Consolas/etc., com peso semibold sintetico)
     * usada pelo editor SQL principal. Ponto de acesso publico para qualquer
     * OUTRA area de texto monoespacado do app (visualizador de conteudo de
     * celula, preview de DDL, etc.) usar exatamente a MESMA fonte do editor
     * em vez de {@code Font.MONOSPACED} generico — antes cada area escolhia
     * a sua conta, e o resultado eram 3-4 fontes monoespacadas diferentes
     * convivendo na mesma janela (inconsistencia de tipografia apontada na
     * revisao visual). {@code preferredFamily} nulo/vazio cai na deteccao
     * automatica (melhor fonte instalada); use {@link #monospaceFont(int)}
     * quando nao houver uma preferencia salva do usuario a passar.
     */
    static Font monospaceFont(String preferredFamily, int size) {
        return pickEditorFont(preferredFamily, size);
    }

    /** Atalho de {@link #monospaceFont(String, int)} sem preferencia salva — deteccao automatica. */
    static Font monospaceFont(int size) {
        return pickEditorFont(null, size);
    }

    /**
     * Configura {@code area} como um visualizador de SQL SOMENTE LEITURA com
     * exatamente a MESMA sintaxe destacada do editor principal (mesmo
     * TokenMaker, mesma paleta semantica de cores — ver
     * {@link #applySemanticSyntaxColors}) — usado pelo "DDL (pre-visualizacao)"
     * do assistente de criar/alterar tabela e pela aba "DDL" do dialogo de
     * propriedades do objeto (SHOW CREATE TABLE/VIEW/...). Pedido do "Sistema
     * Semantico de Cores por Tipo de Dado": o DDL exibido deve usar a MESMA
     * cor de palavra-chave/tipo que o editor de consultas, nao um texto plano
     * sem nenhum destaque.
     * <p>
     * Nao acompanha uma troca de tema AO VIVO (diferente do editor principal,
     * que tem {@link #refreshTheme()}) — aceitavel aqui porque estes dois usos
     * sao dialogos fechados/reabertos a cada vez, nunca deixados abertos
     * atravessando um {@code MainWindow#toggleTheme()}.
     */
    public static void styleAsReadOnlySql(RSyntaxTextArea area) {
        area.setEditable(false);
        area.setCodeFoldingEnabled(false);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        ((RSyntaxDocument) area.getDocument()).setSyntaxStyle(new SqlHighlightTokenMaker(area));
        area.setFont(monospaceFont(12));

        boolean dark = FlatLaf.isLafDark();
        String resource = dark ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (InputStream in = SqlEditorPane.class.getResourceAsStream(resource)) {
            if (in != null) {
                Theme.load(in).apply(area);
            }
        } catch (IOException ignored) {
            // Segue com o que a area ja tinha.
        }
        applySemanticSyntaxColors(area.getSyntaxScheme(), dark);
        if (dark) {
            area.setBackground(new Color(0x1E, 0x1E, 0x1E));
            area.setForeground(new Color(0xD4, 0xD4, 0xD4));
        } else {
            area.setBackground(Color.WHITE);
            area.setForeground(Color.BLACK);
        }
    }

    /**
     * Igual a {@link #styleAsReadOnlySql}, so que EDITAVEL (com dobra de
     * codigo ligada) — usado por corpos de SQL editaveis fora do editor
     * principal de consultas: o SELECT de uma view ({@code ViewBuilderDialog})
     * e o corpo de um trigger ({@code TriggerBuilderDialog}). Mesma limitacao
     * de nao acompanhar troca de tema ao vivo (dialogo fechado/reaberto a cada
     * vez), documentada em {@link #styleAsReadOnlySql}.
     */
    static void styleEditableSql(RSyntaxTextArea area) {
        area.setEditable(true);
        area.setCodeFoldingEnabled(true);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        ((RSyntaxDocument) area.getDocument()).setSyntaxStyle(new SqlHighlightTokenMaker(area));
        area.setFont(monospaceFont(12));

        boolean dark = FlatLaf.isLafDark();
        String resource = dark ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (InputStream in = SqlEditorPane.class.getResourceAsStream(resource)) {
            if (in != null) {
                Theme.load(in).apply(area);
            }
        } catch (IOException ignored) {
            // Segue com o que a area ja tinha.
        }
        applySemanticSyntaxColors(area.getSyntaxScheme(), dark);
        if (dark) {
            area.setBackground(new Color(0x1E, 0x1E, 0x1E));
            area.setForeground(new Color(0xD4, 0xD4, 0xD4));
        } else {
            area.setBackground(Color.WHITE);
            area.setForeground(Color.BLACK);
        }
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

    // ---------- Desfazer / Refazer ----------

    private void performUndo() {
        try {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
        } catch (CannotUndoException ignored) {
            // nada a desfazer no momento — sem problema, o atalho so nao faz nada
        }
    }

    private void performRedo() {
        try {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
        } catch (CannotRedoException ignored) {
            // nada a refazer no momento — sem problema
        }
    }

    /**
     * Descarta todo o historico de desfazer/refazer desta aba — chamado pelo
     * {@code MainWindow} logo depois de carregar o SQL salvo/restaurado numa
     * aba nova ({@code textArea().setText(sql)}), pra esse carregamento
     * inicial NUNCA aparecer como algo "desfazivel" (o usuario nao deveria
     * conseguir dar Ctrl+Z e apagar uma query que acabou de abrir).
     */
    public void discardUndoHistory() {
        undoManager.discardAllEdits();
    }

    /**
     * Menu de contexto (botao direito) do editor: Desfazer/Refazer (pelo
     * {@link #undoManager}, nao pelo padrao do RSyntaxTextArea — substitui o
     * menu de fabrica da biblioteca pra nunca ter dois historicos de undo
     * divergentes no mesmo editor) e as acoes basicas de
     * recortar/copiar/colar/selecionar tudo. Estado (habilitado/desabilitado)
     * recalculado toda vez que o menu vai aparecer.
     */
    private JPopupMenu buildEditorPopupMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem undo = new JMenuItem("Desfazer");
        undo.addActionListener(e -> performUndo());
        JMenuItem redo = new JMenuItem("Refazer");
        redo.addActionListener(e -> performRedo());
        menu.add(undo);
        menu.add(redo);
        menu.addSeparator();

        JMenuItem cut = new JMenuItem("Recortar");
        cut.addActionListener(e -> undoManager.runAsSingleEdit(textArea::cut));
        JMenuItem copy = new JMenuItem("Copiar");
        copy.addActionListener(e -> textArea.copy());
        JMenuItem paste = new JMenuItem("Colar");
        paste.addActionListener(e -> pasteFast());
        menu.add(cut);
        menu.add(copy);
        menu.add(paste);
        menu.addSeparator();

        JMenuItem selectAll = new JMenuItem("Selecionar tudo");
        selectAll.addActionListener(e -> textArea.selectAll());
        menu.add(selectAll);
        menu.addSeparator();

        // Roda SO a instrucao sob o cursor (ate o ";" anterior/seguinte, ver
        // SqlStatementLocator) — pedido explicito do usuario: mesma selecao
        // que o duplo-clique ja faz (ver installObjectHover), so acessivel
        // pelo menu de contexto tambem, sem precisar dar duplo-clique antes.
        // Reaproveita currentSql() (usa a selecao quando ha uma) selecionando
        // a instrucao primeiro e so entao chamando onRun — mesmo caminho do
        // botao "Executar"/Ctrl+Enter, sem duplicar a logica de execucao.
        JMenuItem runStatement = new JMenuItem("Executar esta instrucao");
        runStatement.addActionListener(e -> runStatementUnderCaret());
        menu.add(runStatement);

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                undo.setEnabled(undoManager.canUndo());
                redo.setEnabled(undoManager.canRedo());
                boolean hasSelection = textArea.getSelectedText() != null;
                cut.setEnabled(hasSelection && textArea.isEditable());
                copy.setEnabled(hasSelection);
                paste.setEnabled(textArea.isEditable());
                runStatement.setEnabled(!textArea.getText().isBlank());
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                // nada a fazer
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                // nada a fazer
            }
        });
        return menu;
    }
}
