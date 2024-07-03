package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;

import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.IntStream;

public class TreeInstrumenter extends TreeTranslator {


    private final Logger logHelper;
    private final Types types;
    private final TreeHelper helper;
    private final TreeMaker mkTree;
    private final Context context = new Context();
    private final SourceFormat makeNodeId;

    private final TreeHelper.SimpleClass CALL = Logger.FileLoggerSubClasses.Call.clazz;
    private final Type callType;

    private final TreeHelper.SimpleClass Flow = Logger.FileLoggerSubClasses.DefaultControlFlow.clazz;
    private final Type flowType;

    private final TreeHelper.SimpleClass FunFlow = Logger.FileLoggerSubClasses.FunControlFlow.clazz;
    private final Type funFlowType;

    private final TreeHelper.SimpleClass Statement = Logger.FileLoggerSubClasses.Statment.clazz;
    private final Type statementType;

    private final TreeHelper.SimpleClass SubStatement = Logger.FileLoggerSubClasses.SubStatment.clazz;
    private final Type subStatementType;

    private final TreeHelper.SimpleClass Value = Logger.FileLoggerSubClasses.Value.clazz;
    private final Type valueType;


    public TreeInstrumenter(Logger logHelper, TreeHelper helper, Types types, SourceFormat makeNodeId) {
        super();
        this.logHelper = logHelper;
        this.helper = helper;
        this.mkTree = helper.mkTree;
        this.makeNodeId = makeNodeId;
        this.types = types;
        statementType = helper.type(Statement);
        subStatementType = helper.type(SubStatement);
        flowType = helper.type(Flow);
        funFlowType = helper.type(FunFlow);
        callType = helper.type(CALL);
        valueType = helper.type(Value);
    }

    /*******************************************************
     **************** context definition ******************
     *******************************************************/

    private class Context {
        private final Stack<MethodContext> methodStack = new Stack<>();

        public record MethodContext(Symbol.MethodSymbol methodSymbol, Symbol.VarSymbol methodEventGroup,
                                    TreeHelper.SimpleClass flowKind, Type returnType) {
        }

        public Symbol.VarSymbol enterLambda(Type returnType) {
            // TODO search for return value of single method, otherwise we will have problems
            Symbol.VarSymbol methodEventGroup = new Symbol.VarSymbol(0,
                    helper.name("||||" + (symbolNumber++)),
                    funFlowType,
                    this.currentMethod().methodSymbol);
            methodStack.push(new MethodContext(this.currentMethod().methodSymbol, methodEventGroup, FunFlow, returnType));
            return methodEventGroup;
        }

        public Symbol.VarSymbol enterMethod(Symbol.MethodSymbol methodSymbol) {
            Symbol.VarSymbol methodEventGroup = new Symbol.VarSymbol(0,
                    helper.name("||||" + (symbolNumber++)),
                    funFlowType,
                    methodSymbol);
            methodStack.push(new MethodContext(methodSymbol, methodEventGroup, FunFlow, methodSymbol.getReturnType()));
            return methodEventGroup;
        }

        public void exitMethod() {
            methodStack.pop();
        }

        public MethodContext currentMethod() {
            return methodStack.peek();
        }

        public boolean isTop() {
            return methodStack.empty();
        }
    }


    /*******************************************************
     **************** visit node ******************
     *******************************************************/
    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        //TODO we don't support when several classes are defined in the same file (we support inner classes)
        List<JCTree> translated = List.nil();
        for (JCTree def : tree.defs) {
            switch (def) {
                case JCTree.JCClassDecl clazz:
                    translated = translated.append(translate(clazz));
                    break;
                case JCTree.JCMethodDecl meth:
                    translated = translated.append(translate(meth));
                    break;
                case JCTree.JCVariableDecl decl:
                    // to use let expression you need to in a method def
                    // we need some trick
                    // TODO create function that take the result return it and log an executionStep
                    translated = translated.append(decl);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
        tree.defs = translated;
        this.result = tree;
    }



    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
        this.result = logExecutionStep((JCTree.JCMethodInvocation) this.result);
    }

    @Override
    public void visitExec(JCTree.JCExpressionStatement tree) {
        if (tree.expr instanceof JCTree.JCMethodInvocation call && (call.type == null || call.type.equals(helper.voidP))) {
            SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(call);

            super.visitApply((JCTree.JCMethodInvocation) tree.expr);
            JCTree.JCMethodInvocation transCall = (JCTree.JCMethodInvocation) this.result;
            Type.MethodType callType = (Type.MethodType) transCall.meth.type;
            tree.expr = transCall;

            String statement = "stat";
            String subStatement = "sub";
            String callEvent = "call";

            StatementSequenceWithoutBlock builder = makeStatementSequence();
            builder.executeAndBind(statement, (ignored) -> logHelper.statment(tree.toString()), statementType);
            builder.executeAndBind(subStatement, (ignored) -> logHelper.subStatment(tree.toString()), subStatementType);
            builder.execute((binds) -> logHelper.enter(binds.get(statement), Statement));
            builder.execute((binds) -> logHelper.enter(binds.get(subStatement), SubStatement));

            List<String> argNames = List.from(IntStream.range(0, transCall.args.size())
                    .mapToObj(i -> "arg" + i).toList());

            IntStream.range(0, transCall.args.size()).forEach(i ->
                    builder.executeAndBind(argNames.get(i), (ignored) -> transCall.args.get(i), callType.getParameterTypes().get(i))
            );

            this.result = builder
                    .execute((binds) -> logHelper.exit(binds.get(subStatement), SubStatement))
                    .executeAndBind(callEvent, (ignored) -> logHelper.voidCall(format), helper.type(Logger.FileLoggerSubClasses.VoidCall.clazz))
                    .execute((binds) -> {
                        List<Logger.Value> values = argNames.map(name -> logHelper.valueRepr(mkTree.Ident(binds.get(name))));
                        return logHelper.logVoidCall(binds.get(callEvent), values);
                    })
                    .execute((binds) -> {
                        transCall.args = argNames.map(name -> mkTree.Ident(binds.get(name)));
                        return transCall;
                    })
                    .execute((binds) -> logHelper.exit(binds.get(statement), Statement))
                    .block(mkTree.Block(0, List.nil()))
                    .build();
        } else {
            tree.expr = visitStatement(tree.expr);
            this.result = tree;
        }
    }

    @Override
    public void visitAssign(JCTree.JCAssign tree) {
        super.visitAssign(tree);
        this.result = logExecutionStep((JCTree.JCAssign) this.result);
    }

    @Override
    public void visitAssignop(JCTree.JCAssignOp tree) {
        super.visitAssignop(tree);
        this.result = logExecutionStep((JCTree.JCAssignOp) this.result);
    }

    @Override
    public void visitBreak(JCTree.JCBreak tree) {
        System.out.println("visitBreak");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitCase(JCTree.JCCase tree) {
        System.out.println("visitCase");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitConditional(JCTree.JCConditional tree) {
        super.visitConditional(tree);
        System.out.println("visitConditional --- TODO");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitContinue(JCTree.JCContinue tree) {
        System.out.println("visitContinue");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitDoLoop(JCTree.JCDoWhileLoop tree) {
        super.visitDoLoop(tree);
        JCTree.JCDoWhileLoop loop = (JCTree.JCDoWhileLoop) this.result;

        String flowEvent = "flow";

        assertBlock(loop.body);
        loop.body = makeStatementSequence()
                .executeAndBind(flowEvent, (notUsed) -> logHelper.controlFlow(), flowType)
                .execute((binds) -> logHelper.enter(binds.get(flowEvent), Flow))
                .block((JCTree.JCBlock) loop.body)
                .execute((binds) -> logHelper.exit(binds.get(flowEvent), Flow))
                .build();
    }


    @Override
    public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) {
        super.visitForeachLoop(tree);
        JCTree.JCEnhancedForLoop loop = (JCTree.JCEnhancedForLoop) this.result;

        String flowEvent = "flow";

        assertBlock(loop.body);
        loop.body = makeStatementSequence()
                .executeAndBind(flowEvent, (notUsed) -> logHelper.controlFlow(), flowType)
                .execute((binds) -> logHelper.enter(binds.get(flowEvent), Flow))
                .block((JCTree.JCBlock) loop.body)
                .execute((binds) -> logHelper.exit(binds.get(flowEvent), Flow))
                .build();
    }

    @Override
    public void visitForLoop(JCTree.JCForLoop tree) {
        tree.init = translate(tree.init);
        tree.cond = visitStatement(tree.cond);
        tree.step = translate(tree.step);
        tree.body = translate(tree.body);


        String flowEvent = "flow";

        assertBlock(tree.body);
        tree.body = makeStatementSequence()
                .executeAndBind(flowEvent, (notUsed) -> logHelper.controlFlow(), flowType)
                .execute((binds) -> logHelper.enter(binds.get(flowEvent), Flow))
                .block((JCTree.JCBlock) tree.body)
                .execute((binds) -> logHelper.exit(binds.get(flowEvent), Flow))
                .build();
        this.result = tree;
    }

    @Override
    public void visitIf(JCTree.JCIf tree) {
        tree.cond = visitStatement(tree.cond);
        tree.thenpart = handleBranch(tree.thenpart);

        if (tree.elsepart == null) {
            this.result = tree;
            return;
        }


        if (tree.elsepart instanceof JCTree.JCIf treeIf) {
            visitIf(treeIf);

        } else {
            tree.elsepart = handleBranch(tree.elsepart);
        }
        this.result = tree;
    }

    private JCTree.JCStatement handleBranch(JCTree.JCStatement branch) {
        JCTree.JCStatement block = branch;
        if (!(branch instanceof JCTree.JCBlock)) {
            block = mkTree.Block(0, List.of(branch));
        }
        return makeStatementSequence()
                .flow(this, (JCTree.JCBlock) translate(block))
                .build();
    }

    @Override
    public void visitIndexed(JCTree.JCArrayAccess tree) {
        System.out.println("visitIndexed");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitLabelled(JCTree.JCLabeledStatement tree) {
        System.out.println("visitLabelled");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitLambda(JCTree.JCLambda tree) {
        Type returnType = tree.getDescriptorType(types).getReturnType();
        Symbol.VarSymbol flowSymbol = context.enterLambda(returnType);

        super.visitLambda(tree);

        JCTree.JCLambda lambda = (JCTree.JCLambda) this.result;

        if (lambda.body instanceof JCTree.JCExpression expr && expr.type.equals(helper.voidP))
            lambda.body = mkTree.Block(0, List.of(mkTree.Exec(expr)));

        String flowEvent = "flow";

        switch (lambda.body) {
            case JCTree.JCBlock block:
                lambda.body = makeStatementSequence()
                        .executeAndBind(flowEvent, flowSymbol, (notUsed) -> logHelper.functionFlow("lambda"))
                        .execute((binds) -> logHelper.enter(binds.get(flowEvent), FunFlow))
                        .block(block)
                        .execute((binds) -> logHelper.exit(binds.get(flowEvent), FunFlow))
                        .build();
                break;
            case JCTree.JCExpression expr:
                assertThat(!expr.type.equals(helper.voidP), "should not be void");
                lambda.body = makeExpressionSequence()
                        .executeAndBind(flowEvent, (ignored) -> logHelper.functionFlow("lambda"), funFlowType)
                        .execute((binds) -> logHelper.enter(binds.get(flowEvent), FunFlow))
                        .executeAndReturn("-", (ignored) -> expr, returnType)
                        .execute((binds) -> logHelper.exit(binds.get(flowEvent), FunFlow))
                        .build();
                break;
            default:
                throw new UnsupportedOperationException();

        }


        context.exitMethod();
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        Symbol.VarSymbol flowSymbol = context.enterMethod(tree.sym);

        super.visitMethodDef(tree);

        JCTree.JCMethodDecl method = (JCTree.JCMethodDecl) this.result;

        String flowEvent = "flow";
        method.body = makeStatementSequence()
                .executeAndBind(flowEvent, flowSymbol, (notUsed) -> logHelper.functionFlow(tree.name.toString()))
                .execute((binds) -> logHelper.enter(binds.get(flowEvent), FunFlow))
                .block(method.body)
                .execute((binds) -> logHelper.exit(binds.get(flowEvent), FunFlow))
                .build();

        context.exitMethod();
    }

    @Override
    public void visitNewArray(JCTree.JCNewArray tree) {
        //we don't need to do anything for new array, you can click on the result of the expression
        // to inspect it
        super.visitNewArray(tree);
    }

    @Override
    public void visitNewClass(JCTree.JCNewClass tree) {
        super.visitNewClass(tree);
        JCTree.JCNewClass newClass = (JCTree.JCNewClass) this.result;
        this.result = logExecutionStep(newClass);
    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        super.visitReference(tree);
        //TODO
        System.out.println("visitReference");
    }

    @Override
    public void visitAssert(JCTree.JCAssert tree) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(tree);

        super.visitAssert(tree);

        JCTree.JCAssert ass = (JCTree.JCAssert) this.result;

        String res = "res";
        ass.cond = visitStatement(ass.cond);
    }

    @Override
    public void visitReturn(JCTree.JCReturn tree) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(tree);

        if (tree.expr != null) {
            tree.expr = visitStatement(tree.expr);
            tree.expr = makeExpressionSequence()
                    .executeAndReturn("-", (notUsed) -> tree.expr, context.currentMethod().returnType)
                    .execute((notUsed) -> logHelper.exit(context.currentMethod().methodEventGroup, context.currentMethod().flowKind))
                    .build();
        }

        this.result = tree;
    }


    @Override
    public void visitSwitch(JCTree.JCSwitch tree) {
        System.out.println("visitSwitch");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitThrow(JCTree.JCThrow tree) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(tree);
        super.visitThrow(tree);

        String statementEvent = "statementEvent";
        String subStatement = "subStatement";

        JCTree.JCThrow thr = (JCTree.JCThrow) this.result;
        thr.expr = makeExpressionSequence()
                .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(tree.toString()), statementType)
                .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(tree.toString()), subStatementType)
                .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                .execute((binds) -> logHelper.enter(binds.get(subStatement), SubStatement))
                .executeAndReturn("-", (notUsed) -> thr.expr, thr.expr.type)
                .execute((binds) -> logHelper.exit(binds.get(subStatement), SubStatement))
                // remark a throw expression return nothing, but it would mean to change the code, so for now it returns null
                .execute((notUsed) -> logHelper.logSimpleExpression(format, logHelper.valueRepr(helper.nullLiteral), List.nil()))
                .execute((binds) -> logHelper.exit(binds.get(statementEvent), Statement))
                .build();
    }

    @Override
    public void visitTry(JCTree.JCTry tree) {
        super.visitTry(tree);

        JCTree.JCTry tryTree = (JCTree.JCTry) this.result;

        String tryFlow = "try";
        String catchFlow = "catch";
        TreeHelper.SimpleClass TRY = Logger.FileLoggerSubClasses.TryCatch.clazz;
        Type tryType = helper.type(TRY);
        Symbol.VarSymbol tryClass = new Symbol.VarSymbol(0, helper.name("----try"), tryType, context.currentMethod().methodSymbol);

        //TODO what about resources
        //TODO support finally
        tryTree.body = makeStatementSequence()
                .execute((notUsed) -> logHelper.groupMethod("enterTry", tryClass, TRY))
                .block(tryTree.body)
                .execute((notUsed) -> logHelper.groupMethod("exitTry", tryClass, TRY))
                .build();

        tryTree.catchers.forEach(catcher -> {
            catcher.body = makeStatementSequence()
                    .execute((notUsed) -> logHelper.groupMethod("enterCatch", tryClass, TRY))
                    .block(catcher.body)
                    .execute((notUsed) -> logHelper.groupMethod("exitCatch", tryClass, TRY))
                    .build();
        });

        this.result = mkTree.Block(0, List.of(
                mkTree.VarDef(tryClass, logHelper.tryCatch()),
                tryTree));
    }


    @Override
    public void visitTypeTest(JCTree.JCInstanceOf tree) {
        super.visitTypeTest(tree);
        this.result = logExecutionStep((JCTree.JCInstanceOf) this.result);
    }

    @Override
    public void visitUnary(JCTree.JCUnary tree) {
        super.visitUnary(tree);
        this.result = logExecutionStep((JCTree.JCUnary) this.result);
    }

    @Override
    public void visitBinary(JCTree.JCBinary tree) {
        super.visitBinary(tree);
        this.result = logExecutionStep((JCTree.JCBinary) this.result);
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        assertThat(!context.isTop(), "we should not call this method if the variable is defined at the level of the class");

        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(tree);
        super.visitVarDef(tree);
        //we are in a method parameter
        if (tree.init != null) {
            JCTree.JCVariableDecl res = (JCTree.JCVariableDecl) this.result;

            String statementEvent = "statementEvent";
            String subStatement = "subStatement";
            String resultVar = "res";

            tree.init = makeExpressionSequence()
                    .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(tree.toString()), statementType)
                    .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                    .executeAndReturn("-", (noUsed) ->
                                    makeExpressionSequence()
                                            .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(tree.toString()), subStatementType)
                                            .execute((binding) -> logHelper.enter(binding.get(subStatement), SubStatement))
                                            .executeAndReturn(resultVar, (notUsed) -> res.init, res.type)
                                            .execute((binding) -> logHelper.exit(binding.get(subStatement), SubStatement))
                                            .execute((binding) -> {
                                                Logger.Identifier identifier = logHelper.localIdentifier("-", res.name.toString());
                                                Logger.Value value = logHelper.valueRepr(mkTree.Ident(binding.get(resultVar)));
                                                Logger.Write write = logHelper.write(identifier, value);
                                                return logHelper.logSimpleExpression(format, List.of(write));
                                            })
                                            .build()
                            , tree.type)
                    .execute((binds) -> logHelper.exit(binds.get(statementEvent), Statement))
                    .build();
        }
    }

    @Override
    public void visitWhileLoop(JCTree.JCWhileLoop tree) {
        tree.body = translate(tree.body);
        tree.cond = visitStatement(tree.cond);

        String flowEvent = "flow";

        assertBlock(tree.body);
        tree.body = makeStatementSequence()
                .executeAndBind(flowEvent, (notUsed) -> logHelper.controlFlow(), flowType)
                .execute((binds) -> logHelper.enter(binds.get(flowEvent), Flow))
                .block((JCTree.JCBlock) tree.body)
                .execute((binds) -> logHelper.exit(binds.get(flowEvent), Flow))
                .build();
        this.result = tree;
    }

    private static void assertBlock(JCTree.JCStatement stat) {
        assertThat(stat instanceof JCTree.JCBlock, "should be block");
    }

    private static void assertThat(boolean cond, String msg) {
        if (!cond)
            throw new IllegalArgumentException(msg);
    }


    /*******************************************************
     **************** log statement  ******************
     *******************************************************/

    private JCTree.JCExpression visitStatement(JCTree.JCExpression statement) {

        return switch (statement) {
            case JCTree.JCMethodInvocation call -> {
                if (call.type.equals(helper.voidP))
                    throw new IllegalArgumentException("you should use the version that return a statement");
                super.visitApply(call);


                if(call.meth instanceof JCTree.JCFieldAccess access
                        && !(access.selected instanceof JCTree.JCIdent ident && !ident.sym.kind.equals(Kinds.Kind.VAR))
                        && !(access.selected instanceof JCTree.JCFieldAccess f && !f.sym.kind.equals(Kinds.Kind.VAR))
                ){
                    access.selected = makeExpressionSequence()
                            .subStatementAndReturn(this, "-", (ignored)->access.selected, access.selected.type)
                            .build();
                }
                // we do easy thing 1 sub-statement per argument
                Type.MethodType methodType = (Type.MethodType) call.meth.type;
                call.args = List.from(IntStream.range(0, call.args.size()).boxed().toList())
                        .map(i -> makeExpressionSequence()
                                .subStatementAndReturn(this, "-", (ignored) -> call.args.get(i), methodType.getParameterTypes().get(i))
                                .build());

                yield makeExpressionSequence()
                        .statementAndReturn(this, "-", (ignored) -> logExecutionStep(call), call.type)
                        .build();

            }
            case JCTree.JCNewClass call -> {
                super.visitNewClass(call);
                Type.MethodType methodType = (Type.MethodType) call.constructorType;
                call.args = List.from(IntStream.range(0, call.args.size()).boxed().toList())
                        .map(i -> makeExpressionSequence()
                                .subStatementAndReturn(this, "-", (ignored) -> call.args.get(i), methodType.getParameterTypes().get(i))
                                .build());
                yield makeExpressionSequence()
                        .statementAndReturn(this, "-", (ignored) -> logExecutionStep(call), call.type)
                        .build();
            }
            case JCTree.JCAssignOp assign -> {
                super.visitAssignop(assign);
                JCTree.JCAssignOp res = (JCTree.JCAssignOp) this.result;
                //TODO support all different identifiers
                res.rhs = makeExpressionSequence()
                        .subStatementAndReturn(this, "-", (notUsed) -> res.rhs, assign.type)
                        .build();

                yield makeExpressionSequence()
                        .statementAndReturn(this, "-", (notUsed) -> logExecutionStep(res), res.type)
                        .build();
            }
            case JCTree.JCUnary unary -> {
                super.visitUnary(unary);
                JCTree.JCUnary res = (JCTree.JCUnary) this.result;


                yield makeExpressionSequence()
                        .statementAndReturn(this, "-", (notUsed) -> logExecutionStep(res), res.type)
                        .build();

            }
            case JCTree.JCIdent ident -> {
                super.visitIdent(ident);
                JCTree.JCIdent res = (JCTree.JCIdent) this.result;

                String statementEvent = "statementEvent";


                yield makeExpressionSequence()
                        .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(statement.toString()), statementType)
                        .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                        .executeAndReturn("-", (binds) -> {
                            return logExecutionStep(res);
                        }, res.type)
                        .execute((binds) -> logHelper.exit(binds.get(statementEvent), Statement))
                        .build();
            }
            case JCTree.JCAssign assign -> {
                super.visitAssign(assign);
                JCTree.JCAssign res = (JCTree.JCAssign) this.result;

                String statementEvent = "statementEvent";
                String subStatement = "subStatement";


                yield makeExpressionSequence()
                        .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(statement.toString()), statementType)
                        .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                        .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(statement.toString()), subStatementType)
                        .executeAndReturn("-", (binds) -> {

                            res.rhs = makeExpressionSequence()
                                    .execute((notUsed) -> logHelper.enter(binds.get(subStatement), SubStatement))
                                    .executeAndReturn("-", (notUsed) -> res.rhs, res.type)
                                    .execute((notUsed) -> logHelper.exit(binds.get(subStatement), SubStatement))
                                    .build();

                            return logExecutionStep(res);
                        }, res.type)
                        .execute((binds) -> logHelper.exit(binds.get(statementEvent), Statement))
                        .build();
            }
            case JCTree.JCBinary binary -> {
                super.visitBinary(binary);
                JCTree.JCBinary res = (JCTree.JCBinary) this.result;



                Type.MethodType opType = (Type.MethodType )binary.operator.type;
                res.lhs = makeExpressionSequence()
                        .subStatementAndReturn(this, "-", (ignored)->res.lhs,opType.argtypes.get(0))
                        .build();
                res.rhs = makeExpressionSequence()
                        .subStatementAndReturn(this, "-", (ignored)->res.rhs,opType.argtypes.get(1))
                        .build();

                yield makeExpressionSequence()
                        .statementAndReturn(this, "-", (notUsed)->logExecutionStep(res), res.type)
                        .build();
            }
            case JCTree.JCInstanceOf test -> {
                test.expr = translate(test.expr);
                test.expr = makeExpressionSequence()
                        .subStatementAndReturn(this, "-", (ignored) -> test.expr, helper.objectP)
                        .build();
                yield makeExpressionSequence()
                        .statementAndReturn(this, "-", (ignored) -> logExecutionStep(test), helper.boolP)
                        .build();
            }
            case JCTree.JCParens parens -> mkTree.Parens(visitStatement(parens.getExpression()));
            case JCTree.JCLiteral lit -> lit;
            case JCTree.JCTypeCast cast -> {
                cast.expr = visitStatement(cast.expr);
                yield  cast;
            }
            default -> throw new IllegalStateException("Unexpected value: " + statement);
        };
    }

    /*******************************************************
     **************** log execution step  ******************
     *******************************************************/

    private JCTree.JCExpression logExecutionStep(JCTree.JCIdent ident) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(ident);

        String res = "result";
        return makeExpressionSequence()
                .executeAndReturn(res, (notUsed) -> ident, ident.type)
                .execute((binds) ->
                        logHelper.logSimpleExpression(
                                format,
                                logHelper.valueRepr(mkTree.Ident(binds.get(res))),
                                List.nil()))
                .build();
    }

    private JCTree.JCExpression logExecutionStep(JCTree.JCUnary unary) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(unary);
        String res = "result";

        JCTree.Tag operator = unary.getTag();

        if (operator == JCTree.Tag.PREINC || operator == JCTree.Tag.PREDEC) {
            String resValue = "value";
            return makeExpressionSequence()
                    .executeAndReturn(res, (notUsed) -> unary, unary.type)
                    .executeAndBind(resValue, (binds) -> logHelper.valueRepr(mkTree.Ident(binds.get(res))).value(), valueType)
                    .execute((binds) -> {
                        Logger.Identifier identifier = logHelper.localIdentifier("-", unary.arg.toString());
                        Logger.Value value = new Logger.Value(mkTree.Ident(binds.get(resValue)));
                        Logger.Write write = logHelper.write(identifier, value);

                        return logHelper.logSimpleExpression(
                                format,
                                value,
                                List.of(write));
                    })
                    .build();
        } else if (operator == JCTree.Tag.POSTINC || operator == JCTree.Tag.POSTDEC) {
            return makeExpressionSequence()
                    .executeAndReturn(res, (notUsed) -> unary, unary.type)
                    .execute((binds) -> {
                        Logger.Identifier identifier = logHelper.localIdentifier("-", unary.arg.toString());
                        JCTree.JCExpression computeRes = mkTree.Binary(operator == JCTree.Tag.POSTINC ? JCTree.Tag.PLUS : JCTree.Tag.MINUS,
                                mkTree.Ident(binds.get(res)),
                                mkTree.Literal(1));
                        Logger.Value result = logHelper.valueRepr(computeRes);

                        Logger.Value value = logHelper.valueRepr(mkTree.Ident(binds.get(res)));
                        Logger.Write write = logHelper.write(identifier, value);

                        return logHelper.logSimpleExpression(
                                format,
                                result,
                                List.of(write));
                    })
                    .build();
        } else {
            return makeExpressionSequence()
                    .executeAndReturn(res, (notUsed) -> unary, unary.type)
                    .execute((binds) ->
                            logHelper.logSimpleExpression(
                                    format,
                                    logHelper.valueRepr(mkTree.Ident(binds.get(res))),
                                    List.nil()))
                    .build();
        }

    }

    private JCTree.JCExpression logExecutionStep(JCTree.JCInstanceOf test) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(test);

        String res = "result";
        return makeExpressionSequence()
                .executeAndReturn(res, (notUsed) -> test, helper.boolP)
                .execute((binds) ->
                        logHelper.logSimpleExpression(
                                format,
                                logHelper.valueRepr(mkTree.Ident(binds.get(res))),
                                List.nil()))
                .build();
    }

    private JCTree.JCExpression logExecutionStep(JCTree.JCBinary binary) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(binary);

        String res = "result";
        return makeExpressionSequence()
                .executeAndReturn(res, (notUsed) -> binary, binary.type)
                .execute((binds) ->
                        logHelper.logSimpleExpression(
                                format,
                                logHelper.valueRepr(mkTree.Ident(binds.get(res))),
                                List.nil()))
                .build();
    }

    private JCTree.JCExpression logExecutionStep(JCTree.JCMethodInvocation call) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(call);

        String res = "result";
        String callEvent = "call";
        List<String> argsName = List.from(IntStream.range(0, call.args.size())
                .mapToObj(i -> "arg" + i).toList());

        ExpressionSequenceWithoutReturn builder = makeExpressionSequence();


        Type.MethodType methodType = (Type.MethodType) call.meth.type;
        IntStream.range(0, call.args.size())
                .forEach(i -> {
                    Type argType = methodType.getParameterTypes().get(i);
                    builder.executeAndBind(argsName.get(i), (notUsed) -> call.args.get(i), argType);
                });


        return builder
                .executeAndBind(callEvent, (notUsed) -> logHelper.call(format), helper.type(Logger.FileLoggerSubClasses.Call.clazz))
                .execute((binds) -> logHelper.logCall(
                        binds.get(callEvent),
                        argsName.map(name -> logHelper.valueRepr(mkTree.Ident(binds.get(name))))))
                .executeAndReturn(res, (binds) -> {
                    call.args = argsName.map(name -> mkTree.Ident(binds.get(name)));
                    return call;
                }, call.type)
                .execute((binds) -> logHelper.logReturn(binds.get(callEvent), logHelper.valueRepr(mkTree.Ident(binds.get(res)))))
                .build();
    }

    private JCTree.JCExpression logExecutionStep(JCTree.JCNewClass newClass) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(newClass);

        String res = "result";
        String callEvent = "call";
        List<String> argsName = List.from(IntStream.range(0, newClass.args.size())
                .mapToObj(i -> "arg" + i).toList());

        ExpressionSequenceWithoutReturn builder = makeExpressionSequence();


        Type.MethodType methodType = (Type.MethodType) newClass.constructorType;
        IntStream.range(0, newClass.args.size())
                .forEach(i -> {
                    Type argType = methodType.getParameterTypes().get(i);
                    builder.executeAndBind(argsName.get(i), (notUsed) -> newClass.args.get(i), argType);
                });


        return builder
                .executeAndBind(callEvent, (notUsed) -> logHelper.call(format), helper.type(Logger.FileLoggerSubClasses.Call.clazz))
                .execute((binds) -> logHelper.logCall(
                        binds.get(callEvent),
                        argsName.map(name -> logHelper.valueRepr(mkTree.Ident(binds.get(name))))))
                .executeAndReturn(res, (binds) -> {
                    newClass.args = argsName.map(name -> mkTree.Ident(binds.get(name)));
                    return newClass;
                }, newClass.type)
                .execute((binds) -> logHelper.logReturn(
                        binds.get(callEvent),
                        new Logger.Value(logHelper.writeReference(mkTree.Ident(binds.get(res))).ref())))
                .build();
    }

    private JCTree.JCExpression logExecutionStep(JCTree.JCAssignOp op) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(op);

        String res = "result";
        String resVal = "val";

        return makeExpressionSequence()
                .executeAndReturn(res, (notUsed) -> op, op.type)
                .executeAndBind(resVal, (binds) -> logHelper.valueRepr(mkTree.Ident(binds.get(res))).value(), valueType)
                .execute((binds) -> {

                    Logger.Identifier identifier = logHelper.localIdentifier("-", op.lhs.toString());
                    Logger.Value value = new Logger.Value(mkTree.Ident(binds.get(resVal)));
                    Logger.Write write = logHelper.write(identifier, value);
                    return logHelper.logSimpleExpression(
                            format,
                            value,
                            List.of(write));
                })
                .build();
    }

    private JCTree.JCExpression logExecutionStep(JCTree.JCAssign assign) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(assign);
        String res = "result";

        return makeExpressionSequence()
                .executeAndReturn(res, (notUsed) -> assign, assign.type)
                .execute((binds) -> {
                    //TODO compute value only 1 time [optimization]
                    Logger.Identifier identifier = logHelper.localIdentifier("-", assign.lhs.toString());
                    Logger.Value value = logHelper.valueRepr(mkTree.Ident(binds.get(res)));
                    Logger.Write write = logHelper.write(identifier, value);
                    return logHelper.logSimpleExpression(
                            format,
                            value,
                            List.of(write));
                })
                .build();
    }

    /*******************************************************
     **************** subStatement helper ******************
     *******************************************************/

    // these methods only add information about event groups
    // we suppose that the tree has already been visited
    private JCTree.JCExpression exitSubstatement(JCTree.JCExpression expr, Symbol event, Type type) {
        return makeExpressionSequence()
                .executeAndReturn("notUsed", (notUsed) -> (JCTree.JCExpression) expr, type)
                .execute((notUsed) -> logHelper.exit(event, Logger.FileLoggerSubClasses.SubStatment.clazz))
                .build();
    }

    private JCTree.JCExpression enterSubstatement(JCTree.JCExpression expr, Symbol event, Type type) {
        return makeExpressionSequence()
                .execute((notUsed) -> logHelper.enter(event, Logger.FileLoggerSubClasses.SubStatment.clazz))
                .executeAndReturn("notUsed", (notUsed) -> (JCTree.JCExpression) expr, type)
                .build();
    }

    /*******************************************************
     **************** make tree sequences ******************
     *******************************************************/

    /**************
     ********* constructors
     **************/

    public StatementSequenceWithoutBlock makeStatementSequence() {
        return StatementSequenceBuilder.make(context.currentMethod().methodSymbol, helper, mkTree);
    }

    public StatementSequenceWithoutBlock makeStatementSequence(Symbol.MethodSymbol symbol) {
        return StatementSequenceBuilder.make(symbol, helper, mkTree);
    }

    public ExpressionSequenceWithoutReturn makeExpressionSequence() {
        return new ExpressionSequenceBuilder(context.currentMethod().methodSymbol, helper, mkTree);
    }

    /**************
     ********* expression sequence
     **************/

    interface ExpressionSequenceWithReturn {


        ExpressionSequenceWithReturn execute(Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType);

        ExpressionSequenceWithReturn execute(Function<Map<String, Symbol>, JCTree.JCExpression> expr);

        ExpressionSequenceWithReturn executeAndBind(String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType);

        JCTree.JCExpression build();
    }

    interface ExpressionSequenceWithoutReturn {


        ExpressionSequenceWithoutReturn execute(Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType);

        ExpressionSequenceWithoutReturn execute(Function<Map<String, Symbol>, JCTree.JCExpression> expr);

        ExpressionSequenceWithoutReturn executeAndBind(String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType);

        ExpressionSequenceWithReturn executeAndReturn(String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType);

        ExpressionSequenceWithReturn statementAndReturn(TreeInstrumenter instr, String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType);

        ExpressionSequenceWithReturn subStatementAndReturn(TreeInstrumenter instr, String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType);

    }

    static class ExpressionSequenceBuilder implements ExpressionSequenceWithReturn, ExpressionSequenceWithoutReturn {
        private Map<String, Symbol> nameToValue = new HashMap<>();
        private final Symbol.MethodSymbol inMethod;
        private final TreeMaker mkTree;
        private final TreeHelper helper;
        private List<ExpressionSequenceBuilder.VarDef> exprSequence = List.nil();
        private Symbol.VarSymbol returnValue = null;

        private record VarDef(Symbol.VarSymbol symbol, JCTree.JCExpression expr) {
        }

        private ExpressionSequenceBuilder(Symbol.MethodSymbol inMethod, TreeHelper helper, TreeMaker mkTree) {
            this.inMethod = inMethod;
            this.helper = helper;
            this.mkTree = mkTree;
        }

        public static ExpressionSequenceWithoutReturn make(Symbol.MethodSymbol inMethod, TreeHelper helper, TreeMaker mkTree) {
            return new ExpressionSequenceBuilder(inMethod, helper, mkTree);
        }

        @Override
        public ExpressionSequenceBuilder execute(Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType) {
            addExpr(expr.apply(nameToValue), exprType);
            return this;
        }

        @Override
        public ExpressionSequenceBuilder execute(Function<Map<String, Symbol>, JCTree.JCExpression> expr) {
            addExpr(expr.apply(nameToValue), helper.intP);
            return this;
        }

        @Override
        public ExpressionSequenceBuilder executeAndBind(String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType) {
            if (nameToValue.containsKey(name))
                throw new IllegalArgumentException("name already exists in mapping");
            nameToValue.put(name, addExpr(expr.apply(nameToValue), exprType));
            return this;
        }

        @Override
        public ExpressionSequenceWithReturn executeAndReturn(String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType) {
            nameToValue.put(name, addExpr(expr.apply(nameToValue), exprType));
            returnValue = (Symbol.VarSymbol) nameToValue.get(name);
            return this;
        }

        @Override
        public ExpressionSequenceWithReturn statementAndReturn(TreeInstrumenter instr, String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType) {
            String statement = "statement";
            return this
                    .executeAndBind(statement, (notUsed) -> instr.logHelper.statment(expr.toString()), instr.statementType)
                    .execute((binds) -> instr.logHelper.enter(binds.get(statement), instr.Statement))
                    .executeAndReturn(name, expr, exprType)
                    .execute((binds) -> instr.logHelper.exit(binds.get(statement), instr.Statement));
        }

        @Override
        public ExpressionSequenceWithReturn subStatementAndReturn(TreeInstrumenter instr, String name, Function<Map<String, Symbol>, JCTree.JCExpression> expr, Type exprType) {
            String statement = "subStatement";
            return this
                    .executeAndBind(statement, (notUsed) -> instr.logHelper.subStatment(expr.toString()), instr.subStatementType)
                    .execute((binds) -> instr.logHelper.enter(binds.get(statement), instr.SubStatement))
                    .executeAndReturn(name, expr, exprType)
                    .execute((binds) -> instr.logHelper.exit(binds.get(statement), instr.SubStatement));
        }

        @Override
        public JCTree.JCExpression build() {
            return mkTree.LetExpr(
                    exprSequence.map(vDef -> mkTree.VarDef(vDef.symbol, vDef.expr)),
                    mkTree.Ident(returnValue)
            ).setType(returnValue.type);
        }

        private Symbol.VarSymbol addExpr(JCTree.JCExpression expr, Type exprType) {
            Symbol.VarSymbol symbol = nextSymbol(exprType);
            exprSequence = exprSequence.append(new VarDef(symbol, expr));
            return symbol;
        }


        private Symbol.VarSymbol nextSymbol(Type type) {
            return new Symbol.VarSymbol(0,
                    helper.name("----" + (symbolNumber++)),
                    type,
                    inMethod);
        }

    }

    /**************
     ********* statement sequence
     **************/

    interface StatementSequenceWithoutBlock {

        //only used for method definition as we have to exit in return, but return will be created before
        //we create the flow object


        StatementSequenceWithoutBlock execute(Function<Map<String, Symbol>, JCTree.JCExpression> stat);

        StatementSequenceWithBlock block(JCTree.JCBlock block);

        StatementSequenceWithBlock flow(TreeInstrumenter instr, JCTree.JCBlock block);

        StatementSequenceWithoutBlock executeAndBind(String name, Function<Map<String, Symbol>, JCTree.JCExpression> stat, Type exprType);

        StatementSequenceWithoutBlock executeAndBind(String name, Symbol.VarSymbol symbol, Function<Map<String, Symbol>, JCTree.JCExpression> stat);

    }

    interface StatementSequenceWithBlock {

        StatementSequenceWithBlock execute(Function<Map<String, Symbol>, JCTree.JCExpression> stat);

        JCTree.JCBlock build();

        StatementSequenceWithBlock executeAndBind(String name, Function<Map<String, Symbol>, JCTree.JCExpression> stat, Type exprType);

    }


    static class StatementSequenceBuilder {
        final Symbol.MethodSymbol inMethod;
        final TreeHelper helper;
        final TreeMaker mkTree;
        private final Map<String, Symbol> nameToValue = new HashMap<>();

        public static StatementSequenceWithoutBlock make(Symbol.MethodSymbol inMethod, TreeHelper helper, TreeMaker mkTree) {
            StatementSequenceBuilder builder = new StatementSequenceBuilder(inMethod, helper, mkTree);
            return builder.new WithoutBlock();
        }

        private StatementSequenceBuilder(Symbol.MethodSymbol inMethod, TreeHelper helper, TreeMaker mkTree) {
            this.inMethod = inMethod;
            this.helper = helper;
            this.mkTree = mkTree;
        }

        /********
         **** without block
         ********/

        private class WithoutBlock implements StatementSequenceWithoutBlock {
            private List<JCTree.JCStatement> before = List.nil();

            @Override
            public WithoutBlock execute(Function<Map<String, Symbol>, JCTree.JCExpression> stat) {
                before = before.append(mkTree.Exec(stat.apply(nameToValue)));
                return this;
            }

            @Override
            public StatementSequenceWithBlock block(JCTree.JCBlock block) {
                return StatementSequenceBuilder.this.new WithBlock(block, before);
            }

            @Override
            public StatementSequenceWithBlock flow(TreeInstrumenter instr, JCTree.JCBlock block) {
                String flow = "flow";
                return this.executeAndBind(flow, (ignored) -> instr.logHelper.controlFlow(), instr.flowType)
                        .execute((binds) -> instr.logHelper.enter(binds.get(flow), instr.Flow))
                        .block(block)
                        .execute((binds) -> instr.logHelper.exit(binds.get(flow), instr.Flow));
            }


            @Override
            public WithoutBlock executeAndBind(String name, Function<Map<String, Symbol>, JCTree.JCExpression> stat, Type exprType) {
                if (nameToValue.containsKey(name))
                    throw new IllegalArgumentException("name already assigned");
                Symbol.VarSymbol symbol = nextSymbol(exprType);
                before = before.append(mkTree.VarDef(symbol, stat.apply(nameToValue)));
                nameToValue.put(name, symbol);
                return this;
            }

            @Override
            public StatementSequenceWithoutBlock executeAndBind(String name, Symbol.VarSymbol symbol, Function<Map<String, Symbol>, JCTree.JCExpression> stat) {
                if (nameToValue.containsKey(name))
                    throw new IllegalArgumentException("name already assigned");

                before = before.append(mkTree.VarDef(symbol, stat.apply(nameToValue)));
                nameToValue.put(name, symbol);
                return this;
            }
        }

        /********
         **** with block
         ********/

        private class WithBlock implements StatementSequenceWithBlock {

            private final JCTree.JCBlock block;

            public WithBlock(JCTree.JCBlock block, List<JCTree.JCStatement> before) {
                this.block = block;
                List<JCTree.JCStatement> joined = before;
                for (JCTree.JCStatement stat : block.stats)
                    joined = joined.append(stat);
                block.stats = joined;
            }

            @Override
            public WithBlock execute(Function<Map<String, Symbol>, JCTree.JCExpression> stat) {
                block.stats = block.stats.append(mkTree.Exec(stat.apply(nameToValue)));
                return this;
            }

            @Override
            public JCTree.JCBlock build() {
                return block;
            }

            @Override
            public WithBlock executeAndBind(String name, Function<Map<String, Symbol>, JCTree.JCExpression> stat, Type exprType) {
                if (nameToValue.containsKey(name))
                    throw new IllegalArgumentException("name already assigned");
                Symbol.VarSymbol symbol = nextSymbol(exprType);
                block.stats = block.stats.append(mkTree.VarDef(symbol, stat.apply(nameToValue)));
                nameToValue.put(name, symbol);
                return this;
            }
        }

        private Symbol.VarSymbol nextSymbol(Type type) {
            return new Symbol.VarSymbol(0,
                    helper.name("****" + (symbolNumber++)),
                    type,
                    inMethod);
        }
    }

    private static int symbolNumber = 0;
}
