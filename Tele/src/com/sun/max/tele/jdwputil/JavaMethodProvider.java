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
package com.sun.max.tele.jdwputil;

import java.lang.reflect.*;
import java.util.logging.*;

import com.sun.max.jdwp.vm.data.*;
import com.sun.max.jdwp.vm.proxy.*;
import com.sun.max.vm.type.*;

class JavaMethodProvider implements MethodProvider {

    private static final Logger LOGGER = Logger.getLogger(JavaMethodProvider.class.getName());

    private Method method;
    private ReferenceTypeProvider holder;
    private VMAccess vm;

    JavaMethodProvider(Method method, ReferenceTypeProvider holder, VMAccess vm) {
        this.method = method;
        this.holder = holder;
        this.vm = vm;
    }

    public int getFlags() {
        return method.getModifiers();
    }

    public LineTableEntry[] getLineTable() {
        return new LineTableEntry[0];
    }

    public String getName() {
        // TODO: Check if this is correct.
        return method.getName();
    }

    public int getNumberOfArguments() {
        int count = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
        for (Class type : method.getParameterTypes()) {
            if (type.equals(double.class) || type.equals(long.class)) {
                count += 2;
            } else {
                count++;
            }
        }
        return count;
    }

    public ReferenceTypeProvider getReferenceTypeHolder() {
        return holder;
    }

    public String getSignature() {
        return SignatureDescriptor.fromJava(method).toString();
    }

    public String getSignatureWithGeneric() {
        // TODO: Check if this is correct.
        return getSignature();
    }

    public VariableTableEntry[] getVariableTable() {
        return new VariableTableEntry[0];
    }

    public VMValue invoke(ObjectProvider object, VMValue[] args, ThreadProvider threadProvider, boolean singleThreaded, boolean nonVirtual) {

        LOGGER.info("Method " + method.getName() + " of class " + method.getDeclaringClass() + " was invoked with arguments: " + args);
        final Object[] arguments = new Object[args.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = args[i].asJavaObject();
        }

        final VMValue objectValue = vm.createObjectProviderValue(object);
        Object instanceObject = null;
        if (objectValue != null) {
            instanceObject = objectValue.asJavaObject();
        }

        Object result = null;
        try {
            method.setAccessible(true);
            result = method.invoke(instanceObject, arguments);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Exception while invoking method " + method.getName(), e);
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.SEVERE, "Exception while invoking method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            LOGGER.log(Level.SEVERE, "Exception while invoking method " + method.getName(), e);
        }

        if (method.getReturnType() == Void.TYPE) {
            return vm.getVoidValue();
        }

        return vm.createJavaObjectValue(result, method.getReturnType());
    }

    public VMValue invokeStatic(VMValue[] args, ThreadProvider threadProvider, boolean singleThreaded) {
        return invoke(null, args, threadProvider, singleThreaded, false);
    }

    public TargetMethodAccess[] getTargetMethods() {
        return new TargetMethodAccess[0];
    }
}
