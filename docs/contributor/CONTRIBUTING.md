# Contributing GraalPython

Thanks for considering to contribute! To get you started, here is a bit of
information about the structure of this implementation.

You will need to sign the [Oracle Contributor Agreement](http://www.graalvm.org/community/contributors/) for us to be able to
merge your work.

Please also take some time to review our [code of conduct](http://www.graalvm.org/community/conduct/) for contributors.

### Getting started

The first thing you want to do is to set up
[mx](https://github.com/graalvm/mx.git). This is the build tool we use to
develop GraalVM languages. You also need LLVM 6, including the `opt` tool -- the
latter is not included on macOS by default, here you can install the homebrew
version of LLVM, which includes this tool. Note that you can use any JDK, and do
not necessarily need GraalVM for development. In that case you'll only be able
to run without the just-in-time compiler, but that can be fine for making and
testing changes that are not performance sensitive.

Once you have `mx` on your `PATH`, you can run `mx build` in this
repository. This will initially download the required dependencies next to the
repository and build Python. If it succeeds without errors, you should already
be able to run `mx python` and get a REPL.

If you just want to copy and paste some commands, these should get you started:

    $ git clone https://github.com/graalvm/mx.git
    $ git clone https://github.com/graalvm/graalpython.git
    $ cd graalpython
    $ ../mx/mx build
    $ ../mx/mx python -c "print(42)"

For development, we recommend running `mx ideinit` next. This will generate
configurations for Eclipse, IntelliJ, and Netbeans so that you can open the
projects in these IDEs. If you use another editor with support for the [Eclipse
language server](https://github.com/eclipse/eclipse.jdt.ls) we also had reports
of useable development setups with that, but it's not something we support.

### Development layout

Besides the source code of the Python interpreter, we have some useful `mx`
functions defined under the `mx.graalpython` directory. As you make changes, you
can test always test them with `mx build && mx python`. Additionally, there are
various "gates" that we use on our CI systems to check any code that goes
in. You can run all of these using `mx python-gate` or just some by using `mx
python-gate --tags [TAG]`. Two interesting gates to run that cover most things
are:

- `python-unittest` - Run the unittests written in Python, including those for the C extension API
- `python-license` - Check that all files have the correct copyright headers applied to them

###### Built-In modules and classes

For the most part, built-in modules and classes are implemented in the
`com.oracle.graal.python.builtins` package. For each module or class, there's
Java class annoted with `@CoreFunctions`. Each function in a module or a class
is implemented in a Node annotated with `@Builtin`. Take a look at the existing
implementations to get a feel for how this is done. For now, when adding new
classes or modules, they need to be added to the list in
`com.oracle.graal.python.builtins.Python3Core`.

Some built-in functions, modules, and classes are implemented in pure Python. The
files for this are in `graalpython/lib-graalpython`. These files are listed in
the Java `com.oracle.graal.python.builtins.Python3Core` class. Take a look at
these files to see what they do. If a file is called exactly as a built-in
module is, it is executed in the context of that module during startup, so some
of our modules are implemented both in Java and Python. If the name matches no
existing module, the file is executed just for the side-effects.

When implementing a new (or fixing an existing) built-in, take a look at the
CPython source. The layout and naming of modules and types is kept similar to
the CPython source so it should be relatively easy to find the right piece of
code. For some special dunder methods (`__add__`, `__getitem__`,
`__getattribute__`, ...) you may have to figure out the C API slot names for
them to find the right piece of code (`nb_add`, `sq_item`, `tp_getattr`, ...).

You will find that often there are specific C API methods that are called to
convert or coerce arguments, to look up methods either starting on the object or
only on the class, to call a callable object or invoke a method, and more. In
general, most of these methods should have equivalents in our
`PythonObjectLibrary`. See the
[IMPLEMENTATION_DETAILS.md](./IMPLEMENTATION_DETAILS.md) file for details on
that library. If something is missing that is commonly used, we probably have
some Node for it, but it may be a good idea to add it to the
`PythonObjectLibrary` for easier discovery.

Sometimes, you will not easily find what exactly happens for a given piece of
code when that involves more than just a simple built-in call. The `dis` module
on CPython can often help get an angle on what a particular piece of code is
doing. You can call `dis.dis` on any Python function and it will print you
details of the bytecode and associated data, which can be a good starting point
to browse through the CPython source.

###### Python C API

The C implementation and headers for our C API are in
`graalpython/com.oracle.graal.python.cext`. The naming is analogous to C
Python's source names. This folder also includes a `modules` folder for built-in
modules that we have adapted from C Python.

### Debug options

The GraalVM implementation of Python provides proper debug options. It is possible to either debug the Python code, using Chrome debugger,   
or the java code, using your preferred IDE.
The following commands should be executed in a virtualenv environment, which provides a graalpython executable.

For debug Python side code call this:

```
graalpython --inspect your_script.py
```

This will open a debug server, which can be accessed in Chrome Browser under URL `chrome://inspect`.

For debugging java implemented code execute:

```
graalpython --experimental-options -debug-java your_script.py
```

The command will also start a debug server, which can be used in an IDE. If the IDE was initialized properly
by using the command mentioned above, the existing `GraalDebug` run configuration can be used to debug.

### Advanced commands to develop and debug

Here are some advanced commands to debug test failures and fix issues.

First, we have three sets of unittests in the base repository:
1. Our own Python-bases unittests
2. JUnit tests
3. Python's standard library tests

To run the first, you can use this command:

    mx python-gate --tags python-unittest

If some of the tests fail, you can re-run just a single test like this,
substituting TEST-PATTERN (and possibly the file glob on the third line) with
the test you want to run. Note that you can insert `-d` to debug on the Java
level or use `--inspect` to debug in the Chrome debugger.

    mx [-d] graalpytest [--inspect] test_*.py \
        -k TEST-PATTERN

To run the JUnit tests, you can use this command:

    mx python-gate --tags python-junit

To run a subset of the tests, you can use the following. Again, you can use `-d`
to attach with a Java debugger.

    mx [-d] punittest JAVA-TEST-CLASSNAME

To run the Python standard library tests, you can use the following:

    mx python-gate --tags python-tagged-unittest

Note that we use "tag files", small `.txt` files that select which tests to run,
so we only run tests that we know should pass. To run a subset of those tests,
use the following command. However, the way we run those tests is by spawning a
sub-process for every stdlib tests, to avoid interference while our
implementation isn't quite ready, so you have to put the flags somewhere else to
debug. You can see `-debug-java` and `--inspect` below, to debug in Java
debugger or Chromium, respectively.

    mx python-run-cpython-unittest [-debug-java] [--inspect] NAME-OF-CPYTHON-UNITTEST

A tag file can be regenerated with

    mx python-retag-unittests NAME-OF-CPYTHON-UNITTEST

There's also multiple other gates that may fail with changes. One of these is
our *style* gate, which checks formatting rules and copyrights. To auto-fix most
issues, run the following command. Anything that's reported as error after this
command you have to fix manually.

    mx python-style --fix

Another important gate is the gate that checks if you broke the native image
building. To test if building a native image still works, you can use the
following command. This will create a native executable called `graalpython` and
print its path as the last output, if successful.

    mx python-svm

If you made changes to the parser, you may have to regenerate the golden files
like so:

    find graalpython -name *.scope -delete
    find graalpython -name *.tast -delete
    mx punittest com.oracle.graal.python.test.parser


### Benchmarking

We use the `mx` facilities for benchmarking. Use this to list the available
Python suites and VM configurations:

    mx benchmark --list

If you just want to run a single benchmark from, for example, the `meso` suite,
you can use this:

    mx benchmark meso --list

Then if you want to run something, use (for example):

    mx benchmark meso:nbody3

To select which Python VM you want to use, you can pass the arguments separated
by `--`:

    mx benchmark meso:nbody3 -- --python-vm=cpython

For additional arguments to the Python launcher, you can separate them by
another double-dash:

    mx benchmark meso:nbody3 -- --python-vm=graalpython -- --python.EmulateJython -Dgraal.Dump= -Dgraal.MethodFilter=*measure*
