/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins.method;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.text.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import com.sun.max.asm.*;
import com.sun.max.collect.*;
import com.sun.max.ins.*;
import com.sun.max.ins.constant.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.object.*;
import com.sun.max.ins.value.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.debug.*;
import com.sun.max.tele.method.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.value.*;

/**
 * A table-based viewer for an (immutable) section of {@link TargetCode} in the VM.
 * Supports visual effects for execution state, and permits user selection
 * of instructions for various purposes (e.g. set breakpoint).
 *
 * @author Mick Jordan
 * @author Doug Simon
 * @author Michael Van De Vanter
 */
public class JTableTargetCodeViewer extends TargetCodeViewer {

    private final Inspection inspection;
    private final TargetCodeTable table;
    private final TargetCodeTableModel model;
    private final TargetCodeTableColumnModel columnModel;
    private final TableColumn[] columns;
    private final OperandsRenderer operandsRenderer;
    private final SourceLineRenderer sourceLineRenderer;

    public JTableTargetCodeViewer(Inspection inspection, MethodInspector parent, TeleTargetRoutine teleTargetRoutine) {
        super(inspection, parent, teleTargetRoutine);
        this.inspection = inspection;
        this.operandsRenderer = new OperandsRenderer();
        this.sourceLineRenderer = new SourceLineRenderer();
        this.model = new TargetCodeTableModel(teleTargetRoutine.getInstructions());
        this.columns = new TableColumn[TargetCodeColumnKind.VALUES.length()];
        this.columnModel = new TargetCodeTableColumnModel();
        this.table = new TargetCodeTable(inspection, model, columnModel);
        createView();
    }

    @Override
    protected void createView() {
        super.createView();

        // Set up toolbar
        JButton button = new InspectorButton(inspection, inspection.actions().toggleTargetCodeBreakpoint());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugToggleBreakpointbuttonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, inspection.actions().debugStepOver());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepOverButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, inspection.actions().debugSingleStep());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepInButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, inspection.actions().debugReturnFromFrame());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepOutButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, inspection.actions().debugRunToSelectedInstruction());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugRunToCursorButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, inspection.actions().debugResume());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugContinueButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, inspection.actions().debugPause());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugPauseButtonIcon());
        toolBar().add(button);

        toolBar().add(Box.createHorizontalGlue());

        toolBar().add(new TextLabel(inspection(), "Target Code"));

        toolBar().add(Box.createHorizontalGlue());

        addActiveRowsButton();

        addSearchButton();

        final JButton viewOptionsButton = new InspectorButton(inspection(), new AbstractAction("View...") {
            public void actionPerformed(ActionEvent actionEvent) {
                final TargetCodeViewerPreferences globalPreferences = TargetCodeViewerPreferences.globalPreferences(inspection());
                new TableColumnVisibilityPreferences.ColumnPreferencesDialog<TargetCodeColumnKind>(inspection(), "TargetCode View Options", columnModel.preferences(), globalPreferences);
            }
        });
        viewOptionsButton.setToolTipText("Target code view options");
        viewOptionsButton.setText(null);
        viewOptionsButton.setIcon(style().generalPreferencesIcon());
        toolBar().add(viewOptionsButton);

        toolBar().add(Box.createHorizontalGlue());
        addCodeViewCloseButton();

        final JScrollPane scrollPane = new InspectorScrollPane(inspection(), table);
        add(scrollPane, BorderLayout.CENTER);

        refresh(true);
        JTableColumnResizer.adjustColumnPreferredWidths(table);
    }

    @Override
    protected int getRowCount() {
        return table.getRowCount();
    }

    @Override
    protected int getSelectedRow() {
        return table.getSelectedRow();
    }

    @Override
    protected void setFocusAtRow(int row) {
        inspection.focus().setCodeLocation(maxVM().createCodeLocation(model.rowToInstruction(row).address), false);
    }

    @Override
    protected RowTextSearcher getRowTextSearcher() {
        return new TableRowTextSearcher(inspection, table);
    }

    /**
     * Global code selection has been set; return true iff the view contains selection.
     * Update even when the selection is set to the same value, because we want
     * that to force a scroll to make the selection visible.
     */
    @Override
    public boolean updateCodeFocus(TeleCodeLocation teleCodeLocation) {
        return table.updateCodeFocus(teleCodeLocation);
    }

    /**
     * Data model representing a block of disassembled code, one row per instruction.
     */
    private final class TargetCodeTableModel extends AbstractTableModel {

        private final IndexedSequence<TargetCodeInstruction> instructions;

        public TargetCodeTableModel(IndexedSequence<TargetCodeInstruction> instructions) {
            this.instructions = instructions;
        }

        public int getColumnCount() {
            return TargetCodeColumnKind.VALUES.length();
        }

        public int getRowCount() {
            return instructions.length();
        }

        public Object getValueAt(int row, int col) {
            final TargetCodeInstruction targetCodeInstruction = rowToInstruction(row);
            switch (TargetCodeColumnKind.VALUES.get(col)) {
                case TAG:
                    return null;
                case NUMBER:
                    return row;
                case ADDRESS:
                    return targetCodeInstruction.address;
                case POSITION:
                    return targetCodeInstruction.position;
                case LABEL:
                    final String label = targetCodeInstruction.label;
                    return label != null ? label + ":" : "";
                case INSTRUCTION:
                    return targetCodeInstruction.mnemonic;
                case OPERANDS:
                    return targetCodeInstruction.operands;
                case SOURCE_LINE:
                    return "";
                case BYTES:
                    return targetCodeInstruction.bytes;
                default:
                    throw new RuntimeException("Column out of range: " + col);
            }
        }

        @Override
        public Class< ? > getColumnClass(int col) {
            switch (TargetCodeColumnKind.VALUES.get(col)) {
                case TAG:
                    return Object.class;
                case NUMBER:
                    return Integer.class;
                case ADDRESS:
                    return Address.class;
                case POSITION:
                    return Integer.class;
                case LABEL:
                case INSTRUCTION:
                case OPERANDS:
                case SOURCE_LINE:
                    return String.class;
                case BYTES:
                    return byte[].class;
                default:
                    throw new RuntimeException("Column out of range: " + col);
            }
        }

        public TargetCodeInstruction rowToInstruction(int row) {
            return instructions.get(row);
        }

        /**
         * @param address a code address in the VM.
         * @return the row in this block of code containing an instruction starting at the address, -1 if none.
         */
        public int findRow(Address address) {
            int row = 0;
            for (TargetCodeInstruction targetCodeInstruction : instructions) {
                if (targetCodeInstruction.address.equals(address)) {
                    return row;
                }
                row++;
            }
            return -1;
        }
    }

    /**
     * A table specialized for displaying a block of disassembled target code, one instruction per line.
     */
    private final class TargetCodeTable extends InspectorTable {

        TargetCodeTable(Inspection inspection, TargetCodeTableModel model, TargetCodeTableColumnModel tableColumnModel) {
            super(inspection, model, tableColumnModel);
            setFillsViewportHeight(true);
            setShowHorizontalLines(style().codeTableShowHorizontalLines());
            setShowVerticalLines(style().codeTableShowVerticalLines());
            setIntercellSpacing(style().codeTableIntercellSpacing());
            setRowHeight(style().codeTableRowHeight());
            setRowSelectionAllowed(true);
            setColumnSelectionAllowed(true);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        }

        @Override
        protected InspectorMenu getDynamicMenu(int row, int col, MouseEvent mouseEvent) {
            if (col == ObjectFieldColumnKind.TAG.ordinal()) {
                final InspectorMenu menu = new InspectorMenu();
                final Address address = JTableTargetCodeViewer.this.model.rowToInstruction(row).address;
                menu.add(actions().setTargetCodeBreakpoint(address, "Set breakpoint"));
                menu.add(actions().removeTargetCodeBreakpoint(address, "Unset breakpoint"));
                return menu;
            }
            return null;
        }

        @Override
        public void paintChildren(Graphics g) {
            super.paintChildren(g);
            final int row = getSelectedRow();
            if (row >= 0) {
                g.setColor(style().debugSelectedCodeBorderColor());
                g.drawRect(0, row * getRowHeight(row), getWidth() - 1, getRowHeight(row) - 1);
            }
        }

        @Override
        protected JTableHeader createDefaultTableHeader() {
            return new JTableHeader(getColumnModel()) {
                @Override
                public String getToolTipText(MouseEvent mouseEvent) {
                    final Point p = mouseEvent.getPoint();
                    final int index = getColumnModel().getColumnIndexAtX(p.x);
                    final int modelIndex = getColumnModel().getColumn(index).getModelIndex();
                    return TargetCodeColumnKind.VALUES.get(modelIndex).toolTipText();
                }
            };
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            // The selection in the table has changed; might have happened via user action (click, arrow) or
            // as a side effect of a focus change.
            super.valueChanged(e);
            if (!e.getValueIsAdjusting()) {
                final int selectedRow = getSelectedRow();
                final TargetCodeTableModel targetCodeTableModel = (TargetCodeTableModel) getModel();
                if (selectedRow >= 0 && selectedRow < targetCodeTableModel.getRowCount()) {
                    inspection().focus().setCodeLocation(maxVM().createCodeLocation(targetCodeTableModel.rowToInstruction(selectedRow).address), true);
                }
            }
        }

        /**
         * Global code selection has been set; return true iff the view contains selection.
         * Update even when the selection is set to the same value, because we want
         * that to force a scroll to make the selection visible.
         */
        public boolean updateCodeFocus(TeleCodeLocation teleCodeLocation) {
            final int oldSelectedRow = getSelectedRow();
            if (teleCodeLocation.hasTargetCodeLocation()) {
                final Address targetCodeInstructionAddress = inspection().focus().codeLocation().targetCodeInstructionAddress();
                if (teleTargetRoutine().targetCodeRegion().contains(targetCodeInstructionAddress)) {
                    final TargetCodeTableModel model = (TargetCodeTableModel) getModel();
                    final int row = model.findRow(targetCodeInstructionAddress);
                    if (row >= 0) {
                        if (row != oldSelectedRow) {
                            changeSelection(row, row, false, false);
                        }
                        scrollToRows(row, row);
                        return true;
                    }
                }
            }
            // View doesn't contain the focus; clear any old selection
            if (oldSelectedRow >= 0) {
                clearSelection();
            }
            return false;
        }

        public void redisplay() {
            // not used pending further refactoring
        }

        public void refresh(boolean force) {
            // not used pending further refactoring
        }

    }

    private final class TargetCodeTableColumnModel extends DefaultTableColumnModel {

        private final TargetCodeViewerPreferences preferences;

        private TargetCodeTableColumnModel() {
            preferences = new TargetCodeViewerPreferences(TargetCodeViewerPreferences.globalPreferences(inspection())) {
                @Override
                public void setIsVisible(TargetCodeColumnKind columnKind, boolean visible) {
                    super.setIsVisible(columnKind, visible);
                    final int col = columnKind.ordinal();
                    if (visible) {
                        addColumn(columns[col]);
                    } else {
                        removeColumn(columns[col]);
                    }
                    JTableColumnResizer.adjustColumnPreferredWidths(table);
                    refresh(true);
                }
            };

            final Address startAddress = model.rowToInstruction(0).address;
            createColumn(TargetCodeColumnKind.TAG, new TagRenderer());
            createColumn(TargetCodeColumnKind.NUMBER, new NumberRenderer());
            createColumn(TargetCodeColumnKind.ADDRESS, new AddressRenderer(startAddress));
            createColumn(TargetCodeColumnKind.POSITION, new PositionRenderer(startAddress));
            createColumn(TargetCodeColumnKind.LABEL, new LabelRenderer(startAddress));
            createColumn(TargetCodeColumnKind.INSTRUCTION, new InstructionRenderer(inspection));
            createColumn(TargetCodeColumnKind.OPERANDS, operandsRenderer);
            createColumn(TargetCodeColumnKind.SOURCE_LINE, sourceLineRenderer);
            createColumn(TargetCodeColumnKind.BYTES, new BytesRenderer(inspection));
        }

        private TargetCodeViewerPreferences preferences() {
            return preferences;
        }

        private void createColumn(TargetCodeColumnKind columnKind, TableCellRenderer renderer) {
            final int col = columnKind.ordinal();
            columns[col] = new TableColumn(col, 0, renderer, null);
            columns[col].setHeaderValue(columnKind.label());
            columns[col].setMinWidth(columnKind.minWidth());
            if (preferences.isVisible(columnKind)) {
                addColumn(columns[col]);
            }
            columns[col].setIdentifier(columnKind);
        }
    }

    /**
     * Return the appropriate color for displaying the row's text depending on whether the instruction pointer is at
     * this row.
     *
     * @param row the row to check
     * @return the color to be used
     */
    private Color getRowTextColor(int row) {
        return isInstructionPointer(row) ? style().debugIPTextColor() : (isCallReturn(row) ? style().debugCallReturnTextColor() : style().defaultCodeColor());
    }

    private final class TagRenderer extends JLabel implements TableCellRenderer, TextSearchable, Prober {
        public Component getTableCellRendererComponent(JTable table, Object ignore, boolean isSelected, boolean hasFocus, int row, int col) {
            setOpaque(true);
            setBackground(rowToBackgroundColor(row));
            final StringBuilder toolTipText = new StringBuilder(100);
            final StackFrameInfo stackFrameInfo = stackFrameInfo(row);
            if (stackFrameInfo != null) {
                toolTipText.append("Stack ");
                toolTipText.append(stackFrameInfo.position());
                toolTipText.append(":  0x");
                toolTipText.append(stackFrameInfo.frame().instructionPointer.toHexString());
                toolTipText.append("  thread=");
                toolTipText.append(inspection.nameDisplay().longName(stackFrameInfo.thread()));
                toolTipText.append("; ");
                if (stackFrameInfo.frame().isTopFrame()) {
                    setIcon(style().debugIPTagIcon());
                    setForeground(style().debugIPTagColor());
                } else {
                    setIcon(style().debugCallReturnTagIcon());
                    setForeground(style().debugCallReturnTagColor());
                }
            } else {
                setIcon(style().debugDefaultTagIcon());
                setForeground(style().debugDefaultTagColor());
            }
            setText(rowToTagText(row));
            final TeleTargetBreakpoint teleTargetBreakpoint = getTargetBreakpointAtRow(row);
            if (teleTargetBreakpoint != null) {
                toolTipText.append(teleTargetBreakpoint);
                if (teleTargetBreakpoint.isEnabled()) {
                    setBorder(style().debugEnabledTargetBreakpointTagBorder());
                } else {
                    setBorder(style().debugDisabledTargetBreakpointTagBorder());
                }
            } else {
                setBorder(style().debugDefaultTagBorder());
            }
            setToolTipText(toolTipText.toString());
            return this;
        }

        public String getSearchableText() {
            return "";
        }

        public void redisplay() {
        }

        public void refresh(boolean force) {
        }
    }

    private final class NumberRenderer extends PlainLabel implements TableCellRenderer {

        public NumberRenderer() {
            super(inspection, "");
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setValue(row);
            setToolTipText("Instruction no. " + row + "in method");
            setBackground(rowToBackgroundColor(row));
            setForeground(getRowTextColor(row));
            return this;
        }
    }

    private final class AddressRenderer extends LocationLabel.AsAddressWithPosition implements TableCellRenderer {

        private final Address entryAddress;

        AddressRenderer(Address entryAddress) {
            super(inspection, 0, entryAddress);
            this.entryAddress = entryAddress;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Address address = (Address) value;
            setValue(address.minus(entryAddress).toInt());
            setColumns(getText().length() + 1);
            setBackground(rowToBackgroundColor(row));
            setForeground(getRowTextColor(row));
            return this;
        }
    }

    private final class PositionRenderer extends LocationLabel.AsPosition implements TableCellRenderer {
        private int position;

        public PositionRenderer(Address entryAddress) {
            super(inspection, 0, entryAddress);
            this.position = 0;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Integer position = (Integer) value;
            if (this.position != position) {
                this.position = position;
                setValue(position);
            }
            setBackground(rowToBackgroundColor(row));
            setForeground(getRowTextColor(row));
            return this;
        }
    }


    private final class LabelRenderer extends LocationLabel.AsTextLabel implements TableCellRenderer {

        public LabelRenderer(Address entryAddress) {
            super(inspection, entryAddress);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Integer position = (Integer) model.getValueAt(row, TargetCodeColumnKind.POSITION.ordinal());
            setLocation(value.toString(), position);
            setFont(style().defaultTextFont());
            setBackground(rowToBackgroundColor(row));
            setForeground(getRowTextColor(row));
            return this;
        }
    }


    private final class InstructionRenderer extends TargetCodeLabel implements TableCellRenderer {
        InstructionRenderer(Inspection inspection) {
            super(inspection, "");
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setBackground(rowToBackgroundColor(row));
            setForeground(getRowTextColor(row));
            final String string = value.toString();
            setText(string);
            setColumns(string.length() + 1);
            return this;
        }
    }


    private interface LiteralRenderer {
        WordValueLabel render(Inspection inspection, String literalLoadText, Address literalAddress);
    }

    static final LiteralRenderer AMD64_LITERAL_RENDERER = new LiteralRenderer() {
        public WordValueLabel render(Inspection inspection, String literalLoadText, final Address literalAddress) {
            final WordValueLabel wordValueLabel = new WordValueLabel(inspection, WordValueLabel.ValueMode.LITERAL_REFERENCE, null) {
                @Override
                public Value fetchValue() {
                    return new WordValue(maxVM().readWord(literalAddress, 0));
                }
            };
            wordValueLabel.setPrefix(literalLoadText.substring(0, literalLoadText.indexOf("[")));
            wordValueLabel.setToolTipSuffix(" from RIP " + literalLoadText.substring(literalLoadText.indexOf("["), literalLoadText.length()));
            wordValueLabel.updateText();
            return wordValueLabel;
        }
    };

    static final LiteralRenderer SPARC_LITERAL_RENDERER = new LiteralRenderer() {
        public WordValueLabel render(Inspection inspection, String literalLoadText, final Address literalAddress) {
            final WordValueLabel wordValueLabel = new WordValueLabel(inspection, WordValueLabel.ValueMode.LITERAL_REFERENCE, null) {
                @Override
                public Value fetchValue() {
                    return new WordValue(maxVM().readWord(literalAddress, 0));
                }
            };
            wordValueLabel.setSuffix(literalLoadText.substring(literalLoadText.indexOf(",")));
            wordValueLabel.setToolTipSuffix(" from " + literalLoadText.substring(0, literalLoadText.indexOf(",")));
            wordValueLabel.updateText();
            return wordValueLabel;
        }
    };

    LiteralRenderer getLiteralRenderer(Inspection inspection) {
        InstructionSet instructionSet = maxVM().vmConfiguration().platform().instructionSet();
        switch (instructionSet) {
            case AMD64:
                return AMD64_LITERAL_RENDERER;
            case SPARC:
                return SPARC_LITERAL_RENDERER;
            case ARM:
            case PPC:
            case IA32:
                FatalError.unimplemented();
                return null;
        }
        ProgramError.unknownCase();
        return null;
    }

    private final class SourceLineRenderer extends PlainLabel implements TableCellRenderer {

        private BytecodeLocation lastBytecodeLocation;
        SourceLineRenderer() {
            super(JTableTargetCodeViewer.this.inspection(), null);
            addMouseListener(new InspectorMouseClickAdapter(inspection()) {
                @Override
                public void procedure(final MouseEvent mouseEvent) {
                    final BytecodeLocation bytecodeLocation = lastBytecodeLocation;
                    if (bytecodeLocation != null) {
                        final JPopupMenu menu = new JPopupMenu();
                        for (BytecodeLocation location = bytecodeLocation; location != null; location = location.parent()) {
                            final StackTraceElement stackTraceElement = location.toStackTraceElement();
                            final String fileName = stackTraceElement.getFileName();
                            if (fileName != null) {
                                final int lineNumber = stackTraceElement.getLineNumber();
                                if (lineNumber > 0) {
                                    if (maxVM().findJavaSourceFile(location.classMethodActor().holder()) != null) {
                                        final BytecodeLocation locationCopy = location;
                                        menu.add(new AbstractAction("Open " + fileName + " at line " + lineNumber) {
                                            public void actionPerformed(ActionEvent e) {
                                                inspection().viewSourceExternally(locationCopy);
                                            }
                                        });
                                    }
                                }
                            }
                        }
                        if (menu.getComponentCount() > 0) {
                            menu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                        }
                    }
                }
            });
        }

        private String toolTipText(StackTraceElement stackTraceElement) {
            String s = stackTraceElement.toString();
            final int openParen = s.indexOf('(');
            s = Classes.getSimpleName(stackTraceElement.getClassName()) + "." + stackTraceElement.getMethodName() + s.substring(openParen);
            final String text = s;
            return text;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final BytecodeLocation bytecodeLocation = rowToBytecodeLocation(row);
            setText("");
            setToolTipText("Source line not available");
            setBackground(rowToBackgroundColor(row));
            if (bytecodeLocation != null) {
                final StackTraceElement stackTraceElement = bytecodeLocation.toStackTraceElement();
                setText(String.valueOf(stackTraceElement.getLineNumber()));
                final StringBuilder stackTrace = new StringBuilder("<html><table cellpadding=\"1%\"><tr><td></td><td>").append(toolTipText(stackTraceElement)).append("</td></tr>");
                for (BytecodeLocation parent = bytecodeLocation.parent(); parent != null; parent = parent.parent()) {
                    stackTrace.append("<tr><td>--&gt;&nbsp;</td><td>").append(toolTipText(parent.toStackTraceElement())).append("</td></tr>");
                }
                setToolTipText(stackTrace.append("</table>").toString());
            }
            lastBytecodeLocation = bytecodeLocation;
            return this;
        }
    }

    private final class OperandsRenderer implements TableCellRenderer, Prober {
        private InspectorLabel[] inspectorLabels = new InspectorLabel[instructions().length()];
        private TargetCodeLabel targetCodeLabel = new TargetCodeLabel(inspection, "");
        private LiteralRenderer literalRenderer = getLiteralRenderer(inspection);

        public void refresh(boolean force) {
            for (InspectorLabel wordValueLabel : inspectorLabels) {
                if (wordValueLabel != null) {
                    wordValueLabel.refresh(force);
                }
            }
        }

        public void redisplay() {
            for (InspectorLabel wordValueLabel : inspectorLabels) {
                if (wordValueLabel != null) {
                    wordValueLabel.redisplay();
                }
            }
            targetCodeLabel.redisplay();
        }

        public Component getTableCellRendererComponent(JTable table, Object ignore, boolean isSelected, boolean hasFocus, int row, int col) {
            InspectorLabel inspectorLabel = inspectorLabels[row];
            if (inspectorLabel == null) {
                final TargetCodeInstruction targetCodeInstruction = model.rowToInstruction(row);
                final String text = targetCodeInstruction.operands;
                if (targetCodeInstruction.targetAddress != null && !teleTargetRoutine().targetCodeRegion().contains(targetCodeInstruction.targetAddress)) {
                    inspectorLabel = new WordValueLabel(inspection, WordValueLabel.ValueMode.CALL_ENTRY_POINT, targetCodeInstruction.targetAddress, table);
                    inspectorLabels[row] = inspectorLabel;
                } else if (targetCodeInstruction.literalSourceAddress != null) {
                    final Address literalAddress = targetCodeInstruction.literalSourceAddress.asAddress();
                    inspectorLabel = literalRenderer.render(inspection, text, literalAddress);
                    inspectorLabels[row] = inspectorLabel;
                } else if (rowToCalleeIndex(row) >= 0) {
                    final PoolConstantLabel poolConstantLabel = PoolConstantLabel.make(inspection, rowToCalleeIndex(row), localConstantPool(), teleConstantPool(), PoolConstantLabel.Mode.TERSE);
                    poolConstantLabel.setToolTipPrefix(text);
                    inspectorLabel = poolConstantLabel;
                    inspectorLabel.setForeground(getRowTextColor(row));
                } else {
                    final StopPositions stopPositions = teleTargetRoutine().getStopPositions();
                    if (stopPositions != null && stopPositions.isNativeFunctionCallPosition(targetCodeInstruction.position)) {
                        final TextLabel textLabel = new TextLabel(inspection, "<native function>", text);
                        inspectorLabel = textLabel;
                        inspectorLabel.setForeground(getRowTextColor(row));
                    } else {
                        inspectorLabel = targetCodeLabel;
                        inspectorLabel.setText(text);
                        inspectorLabel.setToolTipText(null);
                        inspectorLabel.setForeground(getRowTextColor(row));
                    }
                }
            }
            inspectorLabel.setBackground(rowToBackgroundColor(row));
            return inspectorLabel;
        }

    }

    private final class BytesRenderer extends DataLabel.ByteArrayAsHex implements TableCellRenderer {
        BytesRenderer(Inspection inspection) {
            super(inspection, null);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setBackground(rowToBackgroundColor(row));
            setForeground(getRowTextColor(row));
            setValue((byte[]) value);
            return this;
        }
    }

    @Override
    protected void updateView(boolean force) {
        for (TableColumn column : columns) {
            final Prober prober = (Prober) column.getCellRenderer();
            prober.refresh(force);
        }
    }

    @Override
    public void redisplay() {
        for (TableColumn column : columns) {
            final Prober prober = (Prober) column.getCellRenderer();
            prober.redisplay();
        }
        // TODO (mlvdv)  code view hack for style changes
        table.setRowHeight(style().codeTableRowHeight());
        invalidate();
        repaint();
    }

    @Override
    public void print(String name) {
        final MessageFormat header = new MessageFormat(name);
        final MessageFormat footer = new MessageFormat("Maxine: " + codeViewerKindName() + "  Printed: " + new Date() + " -- Page: {0, number, integer}");
        try {
            table.print(JTable.PrintMode.FIT_WIDTH, header, footer);
        } catch (PrinterException printerException) {
            gui().errorMessage("Print failed: " + printerException.getMessage());
        }
    }
}

