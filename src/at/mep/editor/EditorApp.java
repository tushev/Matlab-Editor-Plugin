package at.mep.editor;

import at.mep.KeyReleasedHandler;
import at.mep.Matlab;
import at.mep.debug.Debug;
import at.mep.gui.AutoSwitcher;
import at.mep.gui.ClickHistory;
import at.mep.gui.bookmarks.Bookmarks;
import at.mep.gui.ContextMenu;
import at.mep.gui.fileStructure.FileStructure;
import at.mep.gui.recentlyClosed.RecentlyClosed;
import at.mep.localhistory.LocalHistory;
import at.mep.mepr.MEPR;
import at.mep.prefs.Settings;
import com.mathworks.matlab.api.editor.Editor;
import com.mathworks.matlab.api.editor.EditorApplicationListener;
import com.mathworks.matlab.api.editor.EditorEvent;
import com.mathworks.mde.editor.EditorSyntaxTextPane;
import com.mathworks.widgets.editor.breakpoints.BreakpointView;
import matlabcontrol.MatlabInvocationException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Created by Andreas Justin on 2016 - 02 - 09. */

public class EditorApp {
    public static final Color ENABLED = new Color(179, 203, 111);
    public static final Color DISABLED = new Color(240, 240, 240);
    private static final int WF = JComponent.WHEN_FOCUSED;
    private static List<String> mCallbacks = new ArrayList<>();
    private static List<KeyStroke> keyStrokes = new ArrayList<>();
    private static List<String> actionMapKeys = new ArrayList<>();
    private static EditorApp INSTANCE;
    private static List<Editor> editors = new ArrayList<>();

    public static EditorApp getInstance() {
        if (INSTANCE != null) return INSTANCE;
        INSTANCE = new EditorApp();
        INSTANCE.addListener();
        return INSTANCE;
    }


    /** adds a matlab function call to the matlab call stack */
    @SuppressWarnings("unused") // is used from Matlab
    public static void addMatlabCallback(String string, KeyStroke keyStroke, String actionMapKey) throws Exception {
        if (!testMatlabCallback(string)) {
            throw new Exception("'" + string + "' is not a valid function");
        }
        if (!mCallbacks.contains(string)) {
            mCallbacks.add(string);
            keyStrokes.add(keyStroke);
            actionMapKeys.add(actionMapKey);
            EditorApp.getInstance().setCallbacks();
        } else System.out.println("'" + string + "' already added");
    }

    /**
     * user can test if the passed string will actually be called as intended. will call the function w/o passing any
     * input arguments. Make sure that passed function has a return statement at the beginning if no arguments are passed
     */
    private static boolean testMatlabCallback(String string) {
        try {
            Matlab.getInstance().proxyHolder.get().feval(string);
            return true;
        } catch (MatlabInvocationException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * on clear classes this listener will just be added to the editor application, which isn't the general idea of "good"
     * TODO: fix me
     */
    private void addListener() {
        EditorWrapper.getMatlabEditorApplication().addEditorApplicationListener(new EditorApplicationListener() {
            @Override
            public void editorOpened(Editor editor) {
                if (Debug.isDebugEnabled()) {
                    System.out.println("EditorApp: " + editor.getLongName() + " has been opened");
                }
                setCallbacks();
                Bookmarks.getInstance().setEditorBookmarks(editor);
                Bookmarks.getInstance().enableBookmarksForMatlab(editor);
                if (Settings.getPropertyBoolean("feature.enableRecentlyClosed")) {
                    RecentlyClosed.remFile(EditorWrapper.getFile(editor));
                }
                if (Settings.getPropertyBoolean("feature.enableClickHistory")) {
                    ClickHistory.getINSTANCE().add(editor);
                }
            }

            @Override
            public void editorClosed(Editor editor) {
                if (Debug.isDebugEnabled()) {
                    System.out.println("EditorApp: " + editor.getLongName() + " has been closed");
                }
                editors.remove(editor);
                if (Settings.getPropertyBoolean("feature.enableRecentlyClosed")) {
                    RecentlyClosed.addFile(EditorWrapper.getFile(editor));
                }
            }

            @Override
            public String toString() {
                return this.getClass().toString();
            }
        });
    }

    public Editor getActiveEditor() {
        return EditorWrapper.getActiveEditorSafe();
    }


    public Editor openEditor(File file) {
        return EditorWrapper.openEditor(file);
    }

    public void setCallbacks() {
        // EditorWrapper.getActiveEditor().addEventListener();
        // even worse than DocumentListener

        List<Editor> openEditors = EditorWrapper.getOpenEditors();
        for (final Editor editor : openEditors) {
            EditorSyntaxTextPane editorSyntaxTextPane = EditorWrapper.getEditorSyntaxTextPane(editor);
            if (editorSyntaxTextPane == null) continue;
            addKeyStrokes(editorSyntaxTextPane);
            addCustomKeyStrokes(editorSyntaxTextPane);
            if (editors.contains(editor)) {
                // editor already has listeners, don't any new listeners
                continue;
            }

            if (Debug.isDebugEnabled()) {
                System.out.println("EditorApp:setCallbacks() " + editor.getShortName());
            }

            if (EditorWrapper.isFloating(editor)) {
                // System.out.println("floating");
            }
            editors.add(editor);

            // AutoSwitcher
            AutoSwitcher.addCheckbox();

            // Mouse Listener
            editorSyntaxTextPane.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // clicked doesn't not get fired while mouse is moving
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    switch (e.getButton()) {
                        case 1: {
                            // left
                            if (Settings.getPropertyBoolean("feature.enableClickHistory")) {
                                ClickHistory.getINSTANCE().add(editor);
                            }
                            break;
                        }
                        case 2: {
                            // middle
                            break;
                        }
                        case 3: {
                            // right
                            ContextMenu.contribute(editor);
                            break;
                        }
                        case 4: {
                            // backward
                            if (Settings.getPropertyBoolean("feature.enableClickHistory")) {
                                ClickHistory.getINSTANCE().locationPrevious();
                            }
                            break;
                        }
                        case 5: {
                            // forward
                            if (Settings.getPropertyBoolean("feature.enableClickHistory")) {
                                ClickHistory.getINSTANCE().locationNext();
                            }
                            break;
                        }
                    }
                }
            });

            // Editor event (AutoSwitcher)
            editor.addEventListener(editorEvent -> {
                // Matlab.getInstance().proxyHolder.get().feval("assignin", "base", "editorEvent", editorEvent);
                switch (editorEvent){
                    case ACTIVATED: {
                        if (Settings.getPropertyBoolean("feature.enableDockableWindows")) {
                            FileStructure.getInstance().populateTree();
                        }

                        remKeyStrokes(EditorWrapper.getOpenEditors());
                        CustomShortCutKey.reload();
                        addKeyStrokes(EditorWrapper.getEditorSyntaxTextPane());

                        if (Settings.getPropertyBoolean("feature.enableAutoDetailViewer")
                                || Settings.getPropertyBoolean("feature.enableAutoCurrentFolder")) {

                            AutoSwitcher.doYourThing();

                            EditorWrapper.setDirtyIfLastEditorChanged(editor);
                            EditorWrapper.setIsActiveEditorDirty(true);

                            if (Debug.isDebugEnabled()) {
                                System.out.println("event occurred");
                            }
                        }
                        break;
                    }
                    case CLOSED: {
                        LocalHistory.addHistoryEntry(editor);
                        break;
                    }
                }
            });

            boolean useListener = true;
            if (useListener) {
                KeyListener[] keyListeners = editorSyntaxTextPane.getKeyListeners();
                for (KeyListener keyListener1 : keyListeners) {
                    if (keyListener1.toString().equals(KeyReleasedHandler.getKeyListener().toString())) {
                        editorSyntaxTextPane.removeKeyListener(keyListener1);
                       // this will assure that the new key listener is added and the previous one is removed
                       // while matlab is still running and the .jar is replaced
                    }
                }
                editorSyntaxTextPane.addKeyListener(KeyReleasedHandler.getKeyListener());
            }

            // document listener
            editorSyntaxTextPane.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    if (Debug.isDebugEnabled()) {
                        System.out.println("EditorApp: " + "insertUpdate");
                    }
                    Bookmarks.getInstance().adjustBookmarks(e, true);
                    try {
                        String insertString = e.getDocument().getText(e.getOffset(), e.getLength());
                        if (insertString.equals("%")) MEPR.doReplace();
                    } catch (BadLocationException ignored) {
                        ignored.printStackTrace();
                    }
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    if (Debug.isDebugEnabled()) {
                        System.out.println("EditorApp: " + "removeUpdate");
                    }
                    Bookmarks.getInstance().adjustBookmarks(e, false);
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    if (Debug.isDebugEnabled()) {
                        System.out.println("EditorApp: " + "changedUpdate");
                    }
                    EditorWrapper.setDirtyIfLastEditorChanged(editor);
                    EditorWrapper.setIsActiveEditorDirty(true);
                }
            });
        }

        // breakpointview color
        if (Settings.containsKey("bpColor")) {
            colorizeBreakpointView(Settings.getPropertyColor("bpColor"));
        } else {
            colorizeBreakpointView(ENABLED);
        }
    }

    private void addDocumentListener() {

    }

    private void addCustomKeyStrokes(EditorSyntaxTextPane editorSyntaxTextPane) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            editorSyntaxTextPane.getInputMap(WF).put(keyStrokes.get(i), actionMapKeys.get(i));
            final int finalI = i;
            editorSyntaxTextPane.getActionMap().put(actionMapKeys.get(i), new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        Matlab.getInstance().proxyHolder.get().feval(mCallbacks.get(finalI), e);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            });
        }
    }

    private void remKeyStrokes(List<Editor> editors) {
        for (Editor e : editors) {
            remKeyStrokes(EditorWrapper.getEditorSyntaxTextPane(e));
        }
    }
    private void remKeyStrokes(EditorSyntaxTextPane editorSyntaxTextPane) {
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getDEBUG());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getExecuteCurrentLines());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getDeleteLines());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getDuplicateLine());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getMoveLineUp());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getMoveLineDown());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getFileStructure());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getRecentlyClosed());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getClipboardStack());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getCopySelectedText());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getCutSelectedText());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getLiveTemplateViewer());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getQuickSearchMepr());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getBookmarkViewer());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getToggleBookmark());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getBreakpointViewer());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getSave());
        editorSyntaxTextPane.getInputMap(WF).remove(CustomShortCutKey.getLocalHistory());
    }

    private void addKeyStrokes(EditorSyntaxTextPane editorSyntaxTextPane) {
        // NOTE: enable/disable feature cannot be checked here. the problem in the current design is, that matlab would
        //       need a restart after enabling features afterwards. that's why the features are checked in the
        //       "EMEPAction" Class

        // DEBUG
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getDEBUG(), "MEP_DEBUG");
        editorSyntaxTextPane.getActionMap().put("MEP_DEBUG", EMEPAction.MEP_DEBUG.getAction());

        // CURRENT LINES
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getExecuteCurrentLines(), "MEP_EXECUTE_CURRENT_LINE");
        editorSyntaxTextPane.getActionMap().put("MEP_EXECUTE_CURRENT_LINE", EMEPAction.MEP_EXECUTE_CURRENT_LINE.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getDeleteLines(), "MEP_DELETE_CURRENT_LINES");
        editorSyntaxTextPane.getActionMap().put("MEP_DELETE_CURRENT_LINES", EMEPAction.MEP_DELETE_CURRENT_LINES.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getDuplicateLine(), "MEP_DUPLICATE_CURRENT_LINE_OR_SELECTION");
        editorSyntaxTextPane.getActionMap().put("MEP_DUPLICATE_CURRENT_LINE_OR_SELECTION", EMEPAction.MEP_DUPLICATE_CURRENT_LINE_OR_SELECTION.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getMoveLineUp(), "MEP_MOVE_CURRENT_LINE_UP");
        editorSyntaxTextPane.getActionMap().put("MEP_MOVE_CURRENT_LINE_UP", EMEPAction.MEP_MOVE_CURRENT_LINE_UP.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getMoveLineDown(), "MEP_MOVE_CURRENT_LINE_DOWN");
        editorSyntaxTextPane.getActionMap().put("MEP_MOVE_CURRENT_LINE_DOWN", EMEPAction.MEP_MOVE_CURRENT_LINE_DOWN.getAction());

        // FILE STRUCTURE
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getFileStructure(), "MEP_SHOW_FILE_STRUCTURE");
        editorSyntaxTextPane.getActionMap().put("MEP_SHOW_FILE_STRUCTURE", EMEPAction.MEP_SHOW_FILE_STRUCTURE.getAction());

        // RECENTLY CLOSED
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getRecentlyClosed(), "MEP_SHOW_RECENTLY_CLOSED");
        editorSyntaxTextPane.getActionMap().put("MEP_SHOW_RECENTLY_CLOSED", EMEPAction.MEP_SHOW_RECENTLY_CLOSED.getAction());

        // CLIPBOARD
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getClipboardStack(), "MEP_SHOW_CLIP_BOARD_STACK_EDT");
        editorSyntaxTextPane.getActionMap().put("MEP_SHOW_CLIP_BOARD_STACK_EDT", EMEPAction.MEP_SHOW_CLIP_BOARD_STACK_EDT.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getCopySelectedText(), "MEP_COPY_CLIP_BOARD");
        editorSyntaxTextPane.getActionMap().put("MEP_COPY_CLIP_BOARD", EMEPAction.MEP_COPY_CLIP_BOARD.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getCutSelectedText(), "MEP_CUT_CLIP_BOARD");
        editorSyntaxTextPane.getActionMap().put("MEP_CUT_CLIP_BOARD", EMEPAction.MEP_CUT_CLIP_BOARD.getAction());

        // MEPR
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getLiveTemplateViewer(), "MEP_MEPR_INSERT");
        editorSyntaxTextPane.getActionMap().put("MEP_MEPR_INSERT", EMEPAction.MEP_MEPR_INSERT.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getQuickSearchMepr(), "MEP_MEPR_QUICK_SEARCH");
        editorSyntaxTextPane.getActionMap().put("MEP_MEPR_QUICK_SEARCH", EMEPAction.MEP_MEPR_QUICK_SEARCH.getAction());

        // BOOKMARKS
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getBookmarkViewer(), "MEP_SHOW_BOOKMARKS");
        editorSyntaxTextPane.getActionMap().put("MEP_SHOW_BOOKMARKS", EMEPAction.MEP_SHOW_BOOKMARKS.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getToggleBookmark(), "MEP_BOOKMARK");
        editorSyntaxTextPane.getActionMap().put("MEP_BOOKMARK", EMEPAction.MEP_BOOKMARK.getAction());

        // BREAKPOINTS
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getBreakpointViewer(), "MEP_SHOW_BREAKPOINTS");
        editorSyntaxTextPane.getActionMap().put("MEP_SHOW_BREAKPOINTS", EMEPAction.MEP_SHOW_BREAKPOINTS.getAction());

        // File History
        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getSave(), "MEP_SAVE");
        editorSyntaxTextPane.getActionMap().put("MEP_SAVE", EMEPAction.MEP_SAVE.getAction());

        editorSyntaxTextPane.getInputMap(WF).put(CustomShortCutKey.getLocalHistory(), "MEP_LOCAL_HISTORY");
        editorSyntaxTextPane.getActionMap().put("MEP_LOCAL_HISTORY", EMEPAction.MEP_LOCAL_HISTORY.getAction());
    }

    public void removeCallbacks() {
        List<Editor> openEditors = EditorWrapper.getMatlabEditorApplication().getOpenEditors();
        for (Editor editor : openEditors) {
            EditorSyntaxTextPane editorSyntaxTextPane = EditorWrapper.getEditorSyntaxTextPane(editor);
            editorSyntaxTextPane.removeKeyListener(KeyReleasedHandler.getKeyListener());
        }
        colorizeBreakpointView(DISABLED);
    }

    public void colorizeBreakpointView(Color color) {
        List<Editor> openEditors = EditorWrapper.getMatlabEditorApplication().getOpenEditors();
        for (Editor editor : openEditors) {
            BreakpointView.Background breakpointView = EditorWrapper.getBreakpointView(editor);
            if (breakpointView != null) breakpointView.setBackground(color);
        }
    }
}

//////////////////////////////////////////
// UNUSED CODE
// old  breakpointview color
// List<Component> list = Matlab.getInstance().getComponents("BreakpointView$2");
// for (Component component : list) {
//     component.setBackground(color);
// }
