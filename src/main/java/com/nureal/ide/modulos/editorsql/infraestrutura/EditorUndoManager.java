package com.nureal.ide.modulos.editorsql.infraestrutura;

import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerenciador de desfazer/refazer para editores de texto da aplicacao —
 * pensado pra ser plugado em QUALQUER {@link JTextComponent} (o editor SQL
 * hoje, mas tambem qualquer editor futuro: texto simples ou outra
 * linguagem), garantindo a MESMA experiencia em todos eles.
 *
 * Por que nao usar so o undo padrao do Swing/RSyntaxTextArea: o
 * {@code javax.swing.undo.UndoManager} puro (e o {@code RUndoManager} do
 * RSyntaxTextArea, que extends dele) registra UMA edicao por chamada de
 * {@code insertString}/{@code remove} do Document — ou seja, digitar uma
 * palavra de 5 letras gera 5 edicoes independentes, e um unico Ctrl+Z
 * desfaz so a ULTIMA letra, nao a palavra inteira. Esta classe resolve isso
 * agrupando digitacoes CONTINUAS num unico {@link CompoundEdit}, seguindo as
 * regras abaixo (todas pedidas explicitamente pelo usuario):
 *
 * <ul>
 * <li>Digitacoes continuas (sem interrupcao) viram UM grupo — um Ctrl+Z
 *     remove a "palavra"/trecho inteiro digitado de uma vez.</li>
 * <li>Um grupo se fecha (e o proximo caractere comeca um grupo NOVO) quando:
 *     o cursor pula pra outro lugar sem ser por causa da propria edicao,
 *     o usuario seleciona outro trecho, passa um tempo perceptivel sem
 *     digitar ({@link #TYPING_GROUP_TIMEOUT_MS}), ou uma operacao explicita
 *     (colar, formatar, substituir, etc. — ver {@link #runAsSingleEdit}) e
 *     executada.</li>
 * <li>Operacoes explicitas (colar, formatar, comentar, mover linhas,
 *     duplicar linhas, substituir) sempre viram UMA operacao propria no
 *     historico, nunca se misturam com digitacao ao redor — quem chama essas
 *     acoes deve envolver a mutacao do documento com
 *     {@link #runAsSingleEdit(Runnable)}.</li>
 * <li>Uma nova edicao depois de um ou mais "desfazer" descarta o historico
 *     de "refazer" — comportamento padrao de {@link UndoManager}, herdado
 *     sem nenhum codigo extra.</li>
 * <li>Limite configuravel de operacoes guardadas (ver {@link #setLimit(int)},
 *     herdado de {@link UndoManager} — mais antigas somem primeiro).</li>
 * </ul>
 *
 * Anexa via {@link Document#addUndoableEditListener}, INDEPENDENTE de
 * qualquer undo manager que o proprio componente ja tenha instalado (ex.: o
 * {@code RUndoManager} interno do RSyntaxTextArea continua existindo, mas
 * como nada mais chama {@code textArea.undoLastAction()}/
 * {@code redoLastAction()} depois de instalar este gerenciador — ver
 * {@code SqlEditorPane} — ele fica simplesmente sem uso, sem conflitar).
 */
public final class EditorUndoManager extends UndoManager {

    private static final long serialVersionUID = 1L;

    /** Numero padrao de operacoes (ja agrupadas) guardadas no historico antes de descartar as mais antigas. */
    private static final int DEFAULT_LIMIT = 500;

    /** Pausa (ms) entre digitacoes a partir da qual uma NOVA letra comeca um grupo novo, mesmo sem outra interrupcao. */
    private static final long TYPING_GROUP_TIMEOUT_MS = 600;

    /** Editor ao qual este gerenciador esta ligado no momento (pode trocar, ver {@link #attachTo}). */
    private JTextComponent editor;
    private Document document;
    private final CaretListener caretListener = this::onCaretUpdate;
    private final PropertyChangeListener documentSwapListener = evt -> attachTo(editor);

    /** Grupo (digitacao continua OU operacao atomica) sendo construido no momento; {@code null} = nenhum aberto. */
    private CompoundEdit currentGroup;

    /** Quando {@code > 0}, uma operacao explicita (ver {@link #runAsSingleEdit}) esta em andamento. */
    private int atomicDepth;

    private long lastEditTimeMillis;
    /** Offset onde a PROXIMA insercao precisa comecar pra ser considerada "digitacao continua" (-1 = nenhuma expectativa). */
    private int expectedInsertOffset = -1;
    /** Offset de inicio da ULTIMA remocao — continuidade de backspace/delete (-1 = nenhuma expectativa). */
    private int expectedRemoveOffset = -1;

    /**
     * Fica {@code true} logo depois de processarmos uma edicao, ate ser
     * consumida pela PROXIMA verificacao de cursor — usado pra distinguir "o
     * cursor moveu por causa da PROPRIA edicao que acabamos de processar"
     * (nao deve quebrar o grupo) de "o cursor moveu por outro motivo:
     * clique, setas, F12, etc." (deve quebrar).
     *
     * Truque de ordenacao: o Swing notifica os {@code DocumentListener}
     * internos do proprio {@code JTextComponent} (que reposicionam o cursor
     * apos um insert/remove) ANTES dos {@code UndoableEditListener} — ou
     * seja, o evento de cursor de uma digitacao chega ANTES da nossa
     * {@link #undoableEditHappened}, nao depois. Por isso a verificacao (ver
     * {@link #checkCaretInterruption}) e sempre ADIADA com
     * {@code SwingUtilities.invokeLater}: quando ela realmente roda (no
     * proximo ciclo do EDT), a edicao correspondente ja foi processada e a
     * flag ja esta marcada — nao importa a ordem em que os dois eventos
     * chegaram originalmente, so que ambos ja tenham acontecido.
     */
    private volatile boolean justEdited;

    /**
     * {@code true} enquanto {@link #undo()}/{@link #redo()} estao rodando —
     * desfazer/refazer uma edicao tambem mexe no Document (pra a tela
     * atualizar), o que poderia dar a impressao de uma edicao NOVA pra
     * {@link #undoableEditHappened}; esta flag garante que notificacoes
     * recebidas durante o proprio undo/redo sejam ignoradas, nunca
     * reinseridas no historico (o que corromperia ou duplicaria tudo).
     */
    private boolean undoRedoInProgress;

    /** Ouvintes de "mudou canUndo/canRedo" — pra menus/botoes/atalhos refletirem o estado (ver {@link Listener}). */
    private final List<Listener> listeners = new ArrayList<>();

    /** Notificado sempre que {@code canUndo()}/{@code canRedo()} podem ter mudado. */
    @FunctionalInterface
    public interface Listener {
        void undoRedoStateChanged(boolean canUndo, boolean canRedo);
    }

    public EditorUndoManager(JTextComponent editor) {
        setLimit(DEFAULT_LIMIT);
        attachTo(editor);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fireStateChanged() {
        boolean canUndo = canUndo();
        boolean canRedo = canRedo();
        for (Listener l : listeners) {
            l.undoRedoStateChanged(canUndo, canRedo);
        }
    }

    /**
     * Liga (ou religa) este gerenciador a {@code editor}: passa a ouvir o
     * {@link Document} atual dele (removendo-se do antigo, se houver) e o
     * cursor, e comeca a acompanhar trocas de Document (propriedade
     * {@code "document"}) automaticamente — cobre o caso raro de um editor
     * trocar de documento em tempo de execucao sem perder a integracao.
     */
    private void attachTo(JTextComponent newEditor) {
        if (editor != null) {
            editor.removeCaretListener(caretListener);
            editor.removePropertyChangeListener("document", documentSwapListener);
        }
        if (document != null) {
            document.removeUndoableEditListener(this);
        }
        editor = newEditor;
        document = (newEditor != null) ? newEditor.getDocument() : null;
        if (newEditor != null) {
            newEditor.addCaretListener(caretListener);
            newEditor.addPropertyChangeListener("document", documentSwapListener);
        }
        if (document != null) {
            document.addUndoableEditListener(this);
        }
        currentGroup = null;
        atomicDepth = 0;
        expectedInsertOffset = -1;
        expectedRemoveOffset = -1;
        justEdited = false;
    }

    // ---------- Recebendo edicoes do documento ----------

    @Override
    public synchronized void undoableEditHappened(UndoableEditEvent e) {
        if (undoRedoInProgress) {
            return; // efeito colateral do proprio undo()/redo() — nao e uma edicao nova do usuario
        }
        UndoableEdit edit = e.getEdit();
        if (!edit.isSignificant()) {
            return; // edicao sem efeito de conteudo real (ex.: atributo) — ignora, igual navegacao
        }

        int offset = -1;
        int length = 0;
        DocumentEvent.EventType type = null;
        if (edit instanceof DocumentEvent de) {
            offset = de.getOffset();
            length = de.getLength();
            type = de.getType();
        }

        long now = System.currentTimeMillis();
        boolean continueGroup;
        if (atomicDepth > 0) {
            continueGroup = true; // dentro de uma operacao explicita: tudo vira UM grupo, sem excecao
        } else if (currentGroup == null) {
            continueGroup = false;
        } else if (now - lastEditTimeMillis > TYPING_GROUP_TIMEOUT_MS) {
            continueGroup = false; // pausa perceptivel
        } else if (type == DocumentEvent.EventType.INSERT && offset == expectedInsertOffset) {
            continueGroup = true; // digitando pra frente, uma letra apos a outra
        } else if (type == DocumentEvent.EventType.REMOVE
                && (offset == expectedRemoveOffset || offset + length == expectedRemoveOffset)) {
            continueGroup = true; // Backspace ou Delete continuos
        } else {
            continueGroup = false;
        }

        if (!continueGroup) {
            flushPendingGroup();
            currentGroup = new CompoundEdit();
        }
        currentGroup.addEdit(edit);
        lastEditTimeMillis = now;

        if (type == DocumentEvent.EventType.INSERT) {
            expectedInsertOffset = offset + length;
            expectedRemoveOffset = -1;
        } else if (type == DocumentEvent.EventType.REMOVE) {
            expectedRemoveOffset = offset;
            expectedInsertOffset = -1;
        } else {
            expectedInsertOffset = -1;
            expectedRemoveOffset = -1;
        }
        justEdited = true;
        fireStateChanged();
    }

    /**
     * Cursor mudou de posicao/selecao — a verificacao de verdade e ADIADA
     * pra depois deste ciclo do EDT (ver {@link #justEdited} pro motivo).
     */
    private void onCaretUpdate(CaretEvent e) {
        javax.swing.SwingUtilities.invokeLater(this::checkCaretInterruption);
    }

    /**
     * Roda depois que TODOS os efeitos sincronos da tecla/clique original ja
     * aconteceram (ver {@link #justEdited}): se o cursor mudou por causa da
     * edicao que acabamos de processar, so consome a flag e segue o grupo
     * aberto. Caso contrario (e fora de uma operacao atomica, que tambem
     * costuma mexer o cursor por conta propria, ex.: {@code formatText}
     * reposiciona o cursor no fim), e uma interrupcao de verdade — fecha o
     * grupo de digitacao atual.
     */
    private synchronized void checkCaretInterruption() {
        if (justEdited) {
            justEdited = false;
            return;
        }
        if (atomicDepth > 0) {
            return;
        }
        if (currentGroup != null) {
            flushPendingGroup();
        }
    }

    /** Fecha o grupo aberto (se houver) e o empurra pro historico "de verdade" (herdado de {@link UndoManager}). */
    private void flushPendingGroup() {
        if (currentGroup == null) {
            return;
        }
        CompoundEdit finished = currentGroup;
        currentGroup = null;
        expectedInsertOffset = -1;
        expectedRemoveOffset = -1;
        finished.end();
        if (finished.isSignificant()) {
            super.addEdit(finished);
        } else {
            finished.die();
        }
    }

    // ---------- Operacoes explicitas (colar, formatar, substituir, comentar, mover/duplicar linhas...) ----------

    /**
     * Executa {@code mutation} (que deve mexer no documento do editor) como
     * UMA UNICA operacao no historico, nunca se misturando com digitacao
     * antes ou depois dela — pedido explicito do usuario pra colar, formatar,
     * comentar linhas, mover blocos, duplicar linhas e substituir. Chame isto
     * ao redor de qualquer acao "de comando do editor" que modifique o texto,
     * mesmo que internamente ela faca varias mudancas no documento (ex.:
     * "substituir tudo" trocando N ocorrencias) — tudo vira um Ctrl+Z so.
     */
    public synchronized void runAsSingleEdit(Runnable mutation) {
        flushPendingGroup(); // o que estava sendo digitado antes fica separado
        atomicDepth++;
        if (currentGroup == null) {
            currentGroup = new CompoundEdit();
        }
        try {
            mutation.run();
        } finally {
            atomicDepth--;
            if (atomicDepth == 0) {
                flushPendingGroup(); // fecha a propria operacao como grupo separado do que vier depois
            }
        }
    }

    // ---------- Desfazer / Refazer ----------

    @Override
    public synchronized boolean canUndo() {
        return (currentGroup != null && currentGroup.isSignificant()) || super.canUndo();
    }

    @Override
    public synchronized void undo() throws CannotUndoException {
        flushPendingGroup(); // Ctrl+Z no meio de uma digitacao desfaz o que foi digitado ate agora, tudo de uma vez
        undoRedoInProgress = true;
        try {
            super.undo();
        } finally {
            undoRedoInProgress = false;
        }
        fireStateChanged();
    }

    @Override
    public synchronized void redo() throws CannotRedoException {
        flushPendingGroup();
        undoRedoInProgress = true;
        try {
            super.redo();
        } finally {
            undoRedoInProgress = false;
        }
        fireStateChanged();
    }

    @Override
    public synchronized void discardAllEdits() {
        currentGroup = null;
        atomicDepth = 0;
        expectedInsertOffset = -1;
        expectedRemoveOffset = -1;
        justEdited = false;
        super.discardAllEdits();
        fireStateChanged();
    }
}
