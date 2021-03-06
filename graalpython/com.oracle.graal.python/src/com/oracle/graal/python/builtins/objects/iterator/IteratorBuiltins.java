/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.iterator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.nodes.BuiltinNames.ITER;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfRangeNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PArrayIterator,
                PythonBuiltinClassType.PDictItemIterator, PythonBuiltinClassType.PDictReverseItemIterator,
                PythonBuiltinClassType.PDictKeyIterator, PythonBuiltinClassType.PDictReverseKeyIterator,
                PythonBuiltinClassType.PDictValueIterator, PythonBuiltinClassType.PDictReverseValueIterator})
public class IteratorBuiltins extends PythonBuiltins {

    /*
     * "extendClasses" only needs one of the set of Java classes that are mapped to the Python
     * class.
     */

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IteratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.isExhausted()")
        public Object exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(VirtualFrame frame, PArrayIterator self,
                        @Cached("createClassProfile()") ValueProfile itemTypeProfile,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            SequenceStorage sequenceStorage = self.array.getSequenceStorage();
            if (self.getIndex() < lenNode.execute(sequenceStorage)) {
                // TODO avoid boxing by getting the array's typecode and using primitive return
                // types
                return itemTypeProfile.profile(getItemNode.execute(frame, sequenceStorage, self.index++));
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        int next(PIntegerSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getIntItemNormalized(self.index++);
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        int next(PIntRangeIterator self) {
            if (self.hasNextInt()) {
                return self.nextInt();
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        PInt next(PBigRangeIterator self) {
            if (self.hasNextBigInt()) {
                return factory().createInt(self.nextBigInt());
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        double next(PDoubleSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getDoubleItemNormalized(self.index++);
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        long next(PLongSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getLongItemNormalized(self.index++);
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        public Object next(PBaseSetIterator self,
                        @Cached("createBinaryProfile()") ConditionProfile sizeChanged,
                        @CachedLibrary(limit = "1") HashingStorageLibrary storageLibrary) {
            HashingStorageIterator<Object> iterator = self.getIterator();
            if (iterator.hasNext()) {
                if (sizeChanged.profile(self.checkSizeChanged(storageLibrary))) {
                    throw raise(RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, "Set");
                }
                return iterator.next();
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = {"self.isPSequence()", "!self.isExhausted()"})
        public Object next(VirtualFrame frame, PSequenceIterator self,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage s = getStorage.execute(self.getPSequence());
            if (self.getIndex() < lenNode.execute(s)) {
                return getItemNode.execute(frame, s, self.index++);
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        public Object next(PStringIterator self) {
            if (self.getIndex() < self.value.length()) {
                return Character.toString(self.value.charAt(self.index++));
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()")
        public Object next(PDictView.PBaseDictIterator<?> self,
                        @Cached ConditionProfile sizeChanged,
                        @CachedLibrary(limit = "3") HashingStorageLibrary storageLibrary,
                        @Cached ConditionProfile profile) {
            if (profile.profile(self.hasNext())) {
                if (sizeChanged.profile(self.checkSizeChanged(storageLibrary))) {
                    throw raise(RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, "dictionary");
                }
                return self.next(factory());
            }
            self.setExhausted();
            throw raise(PythonErrorType.StopIteration);
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object next(VirtualFrame frame, PSequenceIterator self,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItem,
                        @Cached IsBuiltinClassProfile profile) {
            try {
                return callGetItem.executeObject(frame, self.getObject(), self.index++);
            } catch (PException e) {
                e.expectIndexError(profile);
                self.setExhausted();
                throw raise(StopIteration);
            }
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object __iter__(Object self) {
            return self;
        }
    }

    @Builtin(name = __LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int lengthHint(PArrayIterator self) {
            return self.array.len() - self.getIndex();
        }

        @Specialization
        public int lengthHint(PIntegerSequenceIterator self) {
            return self.sequence.length() - self.getIndex();
        }

        @Specialization
        public int lengthHint(PIntRangeIterator self) {
            return self.getLength();
        }

        @Specialization
        public double lengthHint(PDoubleSequenceIterator self) {
            return self.sequence.length() - self.getIndex();
        }

        @Specialization
        public long lengthHint(PLongSequenceIterator self) {
            return self.sequence.length() - self.getIndex();
        }

        @Specialization
        public long lengthHint(PBaseSetIterator self) {
            return self.getSet().size() - self.getIndex();
        }

        @Specialization(guards = "self.isPSequence()")
        public Object lengthHint(PSequenceIterator self,
                        @Cached SequenceNodes.LenNode lenNode) {
            return lenNode.execute(self.getPSequence()) - self.getIndex();
        }

        @Specialization
        public Object lengthHint(PStringIterator self) {
            return self.value.length() - self.getIndex();
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object lengthHint(VirtualFrame frame, PSequenceIterator self,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode callLen,
                        @Cached("create(__SUB__, __RSUB__)") LookupAndCallBinaryNode callSub) {
            return callSub.executeObject(frame, callLen.executeObject(frame, self.getObject()), self.getIndex());
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object reduce(PArrayIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(self.array, self.getIndex(), context, pol);
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PBaseSetIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached SequenceStorageNodes.CreateStorageFromIteratorNode storageNode,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(factory().createList(storageNode.execute(frame, self)), self.getIndex(), context, pol);
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PDictView.PBaseDictIterator<?> self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached SequenceStorageNodes.CreateStorageFromIteratorNode storageNode,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(factory().createList(storageNode.execute(frame, self)), context, pol);
        }

        @Specialization
        public Object reduce(PIntegerSequenceIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(factory().createList(self.getSequenceStorage()), self.getIndex(), context, pol);
        }

        @Specialization
        public Object reduce(PPrimitiveIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(factory().createList(self.getSequenceStorage()), self.getIndex(), context, pol);
        }

        @Specialization
        public Object reduce(PStringIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(self.value, self.getIndex(), context, pol);
        }

        @Specialization
        public Object reduce(PIntRangeIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached LenOfRangeNode length,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            int start = self.getReduceStart();
            int stop = self.getReduceStop();
            int step = self.getReduceStep();
            int len = (int) length.execute(start, stop, step);
            return reduceInternal(factory().createIntRange(start, stop, step, len), self.getIndex(), context, pol);
        }

        @Specialization
        public Object reduce(PBigRangeIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached LenOfRangeNode length,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            PInt start = self.getReduceStart();
            PInt stop = self.getReduceStop(factory());
            PInt step = self.getReduceStep();
            PInt len = factory().createInt((BigInteger) length.execute(start, stop, step));
            return reduceInternal(factory().createBigRange(start, stop, step, len), self.getLongIndex(factory()), context, pol);
        }

        @Specialization(guards = "self.isPSequence()")
        public Object reduce(PSequenceIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(self.getPSequence(), self.getIndex(), context, pol);
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object reduceNonSeq(VirtualFrame frame, PSequenceIterator self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("create(__REDUCE__)") LookupAndCallUnaryNode callUnaryNode,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            Object reduce = pol.lookupAttribute(self.getObject(), __REDUCE__);
            Object content = callUnaryNode.executeObject(frame, reduce);
            return reduceInternal(content, self.getIndex(), context, pol);
        }

        private PTuple reduceInternal(Object arg, PythonContext context, PythonObjectLibrary pol) {
            return reduceInternal(arg, null, context, pol);
        }

        private PTuple reduceInternal(Object arg, Object state, PythonContext context, PythonObjectLibrary pol) {
            PythonModule builtins = context.getCore().getBuiltins();
            Object iter = pol.lookupAttribute(builtins, ITER);
            PTuple args = factory().createTuple(new Object[]{arg});
            // callable, args, state (optional)
            if (state != null) {
                return factory().createTuple(new Object[]{iter, args, state});
            } else {
                return factory().createTuple(new Object[]{iter, args});
            }
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        @CompilerDirectives.TruffleBoundary
        public Object reduce(PBigRangeIterator self, Object index,
                        @Cached CastToJavaBigIntegerNode castToJavaBigIntegerNode) {
            BigInteger idx = castToJavaBigIntegerNode.execute(index);
            if (idx.compareTo(BigInteger.ZERO) < 0) {
                idx = BigInteger.ZERO;
            }
            self.setLongIndex(idx);
            return PNone.NONE;
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public Object reduce(PBuiltinIterator self, Object index,
                        @CachedLibrary(value = "index") PythonObjectLibrary pol) {
            int idx = pol.asSize(index);
            if (idx < 0) {
                idx = 0;
            }
            self.index = idx;
            return PNone.NONE;
        }
    }
}
