/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.c1x.ir;

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The base class for pointer access operations.
 *
 * @author Doug Simon
 */
public abstract class PointerOp extends StateSplit {

    /**
     * The kind of value at the address accessed by the pointer operation.
     */
    public final CiKind dataKind;

    public final int opcode;
    protected Value pointer;
    protected Value displacement;
    protected Value offsetOrIndex;
    protected final boolean isVolatile;
    final boolean isPrefetch;

    /**
     * Creates an instruction for a pointer operation. If {@code displacement != null}, the effective of the address of the operation is
     * computed as the pointer plus a byte displacement plus a scaled index. Otherwise, the effective address is computed as the
     * pointer plus a byte offset.
     *
     * @param kind the kind of value produced by this operation
     * @param dataKind the kind of value at the address accessed by the pointer operation
     * @param opcode the opcode of the instruction
     * @param pointer the value producing the pointer
     * @param displacement the value producing the displacement. This may be {@code null}.
     * @param offsetOrIndex the value producing the scaled-index or the byte offset depending on whether {@code displacement} is {@code null}
     * @param stateBefore the state before
     * @param isVolatile {@code true} if the access is volatile
     */
    public PointerOp(CiKind kind, CiKind dataKind, int opcode, Value pointer, Value displacement, Value offsetOrIndex, FrameState stateBefore, boolean isVolatile) {
        super(kind.stackKind(), stateBefore);
        this.opcode = opcode;
        this.pointer = pointer;
        this.dataKind = dataKind;
        this.displacement = displacement;
        this.offsetOrIndex = offsetOrIndex;
        this.isVolatile = isVolatile;
        this.isPrefetch = false;
        if (pointer.isNonNull()) {
            eliminateNullCheck();
        }
    }

    public Value pointer() {
        return pointer;
    }

    public Value index() {
        return offsetOrIndex;
    }

    public Value offset() {
        return offsetOrIndex;
    }

    public Value displacement() {
        return displacement;
    }

    @Override
    public void runtimeCheckCleared() {
        clearState();
    }

    /**
     * Checks whether this field access may cause a trap or an exception, which
     * is if it either requires a null check or needs patching.
     * @return {@code true} if this field access can cause a trap
     */
    @Override
    public boolean canTrap() {
        return needsNullCheck();
    }

    /**
     * Iterates over the input values to this instruction.
     * @param closure the closure to apply to each value
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        pointer = closure.apply(pointer);
        offsetOrIndex = closure.apply(offsetOrIndex);
        if (displacement != null) {
            displacement = closure.apply(displacement);
        }
    }
}
