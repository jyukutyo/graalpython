/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.object;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.object.dsl.Layout;

@ExportLibrary(PythonObjectLibrary.class)
public class PythonObject extends PythonAbstractObject {
    public static final HiddenKey HAS_DICT = new HiddenKey("has_dict");

    private Object storedPythonClass;
    private PHashingCollection dict;
    private final DynamicObject storage;

    public PythonObject(Object pythonClass, DynamicObject storage) {
        assert pythonClass != null;
        this.storedPythonClass = pythonClass;
        this.storage = storage;
    }

    public static Assumption getSingleContextAssumption() {
        return PythonLanguage.getCurrent().singleContextAssumption;
    }

    @ExportMessage
    @GenerateUncached
    public abstract static class SetLazyPythonClass {

        @Specialization(assumptions = "storingClassesInShapes")
        public static void setSingle(PythonObject self, Object cls,
                        @SuppressWarnings("unused") @Cached(value = "getSingleContextAssumption()", allowUncached = true) Assumption storingClassesInShapes) {
            self.storedPythonClass = cls;
            PythonObjectLayoutImpl.INSTANCE.setLazyPythonClass(self.storage, cls);
        }

        @SuppressWarnings("deprecation")
        @Specialization
        public static void setMultiContext(PythonObject self, Object cls) {
            self.storedPythonClass = cls;
            if (PythonObjectLayoutImpl.INSTANCE.getLazyPythonClass(self.storage) != null) {
                // for the builtin class enums, we now should change the shape
                // to the generic one that just doesn't store the class
                Shape shape = self.storage.getShape();
                self.storage.setShapeAndGrow(shape, shape.changeType(emptyShape.getObjectType()));
            }
        }
    }

    /**
     * This is usually final, a fact may be optimized further if the storage turns into a constant.
     */

    @ExportMessage
    public static class GetLazyPythonClass {

        // if object is constant here, storage will also be constant, so the shape
        // lookup is the only thing we need.
        @Specialization(guards = "object.getStorage().getShape() == cachedShape", assumptions = {"storingClassesInShapes"}, limit = "4")
        public static Object getPythonClassCachedSingle(@SuppressWarnings("unused") PythonObject object,
                        @SuppressWarnings("unused") @Cached("object.getStorage().getShape()") Shape cachedShape,
                        @SuppressWarnings("unused") @Cached(value = "getSingleContextAssumption()", allowUncached = true) Assumption storingClassesInShapes,
                        @Cached("object.getInternalLazyPythonClass()") Object klass) {
            return klass;
        }

        public static boolean isBuiltinType(Shape shape) {
            return PythonObject.getLazyClassFromObjectType(shape.getObjectType()) instanceof PythonBuiltinClassType;
        }

        // we can at least cache builtin types in the multi-context case
        @Specialization(guards = {"object.getStorage().getShape() == cachedShape", "isBuiltinType(cachedShape)"}, limit = "4")
        public static Object getPythonClassCached(@SuppressWarnings("unused") PythonObject object,
                        @SuppressWarnings("unused") @Cached("object.getStorage().getShape()") Shape cachedShape,
                        @Cached("object.getInternalLazyPythonClass()") Object klass) {
            return klass;
        }

        @Specialization(replaces = {"getPythonClassCachedSingle", "getPythonClassCached"})
        public static Object getLazyPythonClass(PythonObject object) {
            return object.storedPythonClass;
        }
    }

    @Override
    public final Object getInternalLazyPythonClass() {
        return storedPythonClass;
    }

    public final DynamicObject getStorage() {
        return storage;
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public final Object getAttribute(Object key) {
        return getStorage().get(key, PNone.NO_VALUE);
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public void setAttribute(Object name, Object value) {
        CompilerAsserts.neverPartOfCompilation();
        getStorage().define(name, value);
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public List<String> getAttributeNames() {
        ArrayList<String> keyList = new ArrayList<>();
        for (Object o : getStorage().getShape().getKeyList()) {
            if (o instanceof String && getStorage().get(o) != PNone.NO_VALUE) {
                keyList.add((String) o);
            }
        }
        return keyList;
    }

    @Override
    public int compareTo(Object o) {
        return this == o ? 0 : 1;
    }

    /**
     * Important: toString can be called from arbitrary locations, so it cannot do anything that may
     * execute Python code or rely on a context being available.
     *
     * The Python-level string representation functionality always needs to be implemented as
     * __repr__/__str__ builtins (which in turn can call toString if this already implements the
     * correct behavior).
     */
    @Override
    public String toString() {
        String className = "unknown";
        if (storedPythonClass instanceof PythonManagedClass) {
            className = ((PythonManagedClass) storedPythonClass).getName();
        } else if (storedPythonClass instanceof PythonBuiltinClassType) {
            className = ((PythonBuiltinClassType) storedPythonClass).getName();
        } else if (PGuards.isNativeClass(storedPythonClass)) {
            className = "native";
        }
        return "<" + className + " object at 0x" + Integer.toHexString(hashCode()) + ">";
    }

    @ExportMessage
    @SuppressWarnings("unused")
    @GenerateUncached
    public abstract static class HasDict {
        @Specialization(guards = {"receiver.getStorage().getShape() == cachedShape", "prop == null"}, assumptions = "layoutAssumption", limit = "1")
        public static boolean hasNoDict(PythonObject receiver,
                        @Exclusive @Cached("receiver.getStorage().getShape()") Shape cachedShape,
                        @Exclusive @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                        @Exclusive @Cached("cachedShape.getProperty(HAS_DICT)") Property prop) {
            return false;
        }

        @Specialization(guards = {"receiver.getStorage().getShape() == cachedShape", "prop != null"}, assumptions = "layoutAssumption", limit = "1")
        public static boolean hasDict(PythonObject receiver,
                        @Exclusive @Cached("receiver.getStorage().getShape()") Shape cachedShape,
                        @Exclusive @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                        @Exclusive @Cached("cachedShape.getProperty(HAS_DICT)") Property prop) {
            return true;
        }

        @Specialization(replaces = {"hasDict", "hasNoDict"})
        public static boolean mayHaveDict(PythonObject receiver) {
            return receiver.dict != null;
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    @GenerateUncached
    public abstract static class GetDict {
        @Specialization(guards = {"receiver.getStorage().getShape() == cachedShape", "prop == null"}, assumptions = "layoutAssumption", limit = "1")
        public static PHashingCollection getNoDict(PythonObject receiver,
                        @Exclusive @Cached("receiver.getStorage().getShape()") Shape cachedShape,
                        @Exclusive @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                        @Exclusive @Cached("cachedShape.getProperty(HAS_DICT)") Property prop) {
            return null;
        }

        @Specialization(replaces = {"getNoDict"})
        public static PHashingCollection getDict(PythonObject receiver) {
            return receiver.dict;
        }
    }

    @ExportMessage
    public final void setDict(PHashingCollection dict,
                    @Cached WriteAttributeToDynamicObjectNode writeNode) {
        writeNode.execute(storage, HAS_DICT, true);
        this.dict = dict;
    }

    @Layout(implicitCastIntToLong = true, implicitCastIntToDouble = false)
    protected static interface PythonObjectLayout {
        DynamicObjectFactory createPythonObjectShape(Object lazyPythonClass);

        DynamicObject createPythonObject(DynamicObjectFactory factory);

        Object getLazyPythonClass(DynamicObjectFactory factory);

        Object getLazyPythonClass(ObjectType objectType);

        Object getLazyPythonClass(DynamicObject object);

        void setLazyPythonClass(DynamicObject object, Object value);
    }

    private static final Shape emptyShape = PythonObjectLayoutImpl.INSTANCE.createPythonObjectShape(null).getShape();

    public static Shape freshShape(Object klass) {
        // GR-17243: I would like to enable this assertion, but it breaks
        // building native images under some circumstances.
        // assert (PythonLanguage.getCurrent().singleContextAssumption.isValid()
        // && klass != null) || klass instanceof PythonBuiltinClassType;
        return PythonObjectLayoutImpl.INSTANCE.createPythonObjectShape(klass).getShape();
    }

    public static Shape freshShape() {
        return emptyShape;
    }

    public static Object getLazyClassFromObjectType(ObjectType type) {
        return PythonObjectLayoutImpl.INSTANCE.getLazyPythonClass(type);
    }
}
