/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapperLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
@ExportLibrary(PythonNativeWrapperLibrary.class)
public final class GraalHPyHandle implements TruffleObject {

    private final Object delegate;
    private int id = -1;

    public GraalHPyHandle(Object delegate) {
        this.delegate = delegate;
    }

    @ExportMessage
    boolean isPointer() {
        return id != -1;
    }

    @ExportMessage
    long asPointer() throws UnsupportedMessageException {
        if (!isPointer()) {
            throw UnsupportedMessageException.create();
        }
        return id;
    }

    @ExportMessage
    void toNative(
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        this.id = context.getHPyContext().getHPyHandleForObject(this);
    }

    @ExportMessage
    boolean hasNativeType() {
        return false;
    }

    @ExportMessage
    Object getNativeType() {
        return null;
    }

    @ExportMessage
    Object getDelegate() {
        return delegate;
    }

    @ExportMessage
    Object getNativePointer() {
        return isPointer() ? id : null;
    }

    @ExportMessage
    boolean isNative() {
        return isPointer();
    }

    public GraalHPyHandle copy() {
        return new GraalHPyHandle(delegate);
    }
}
