package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.IntStream;

public class TreeInstrumenter extends TreeTranslator {


    private final Logger logHelper;
    private final TreeHelper helper;
    private final TreeMaker mkTree;
    private final Context context = new Context();
    private final SourceFormat makeNodeId;

    private final TreeHelper.SimpleClass CALL = Logger.FileLoggerSubClasses.Call.clazz;
    private final Type callType;

    private final TreeHelper.SimpleClass Flow = Logger.FileLoggerSubClasses.ControlFlow.clazz;
    private final Type flowType;

    private final TreeHelper.SimpleClass Statement = Logger.FileLoggerSubClasses.Statment.clazz;
    private final Type statementType;

    private final TreeHelper.SimpleClass SubStatement = Logger.FileLoggerSubClasses.SubStatment.clazz;
    private final Type subStatementType;


    public TreeInstrumenter(Logger logHelper, TreeHelper helper, SourceFormat makeNodeId) {
        super();
        this.logHelper = logHelper;
        this.helper = helper;
        this.mkTree = helper.mkTree;
        this.makeNodeId = makeNodeId;
        statementType = helper.type(Statement);
        subStatementType = helper.type(SubStatement);
        flowType = helper.type(Flow);
        callType = helper.type(CALL);
    }

    /*******************************************************
     **************** context definition ******************
     *******************************************************/

    private static class Context {
        private final Stack<MethodContext> methodStack = new Stack<>();

        public record MethodContext(JCTree.JCMethodDecl method, Symbol.VarSymbol methodEventGroup) {
        }

        public void enterMethod(JCTree.JCMethodDecl method, Symbol.VarSymbol methodEventGroup) {
            methodStack.push(new MethodContext(method, methodEventGroup));
        }

        public void exitMethod() {
            methodStack.pop();
        }

        public MethodContext currentMethod() {
            return methodStack.peek();
        }
    }

    /*******************************************************
     **************** visit node ******************
     *******************************************************/

    @Override
    public void visitAnnotatedType(JCTree.JCAnnotatedType tree) {
        System.out.println("visitAnnotatedType");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitAnnotation(JCTree.JCAnnotation tree) {
        System.out.println("visitAnnotation");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        //TODO Instrument
        super.visitApply(tree);
    }

    @Override
    public void visitExec(JCTree.JCExpressionStatement tree) {
        if (tree.expr instanceof JCTree.JCMethodInvocation call && call.type.equals(helper.voidP)) {
            //TODO instrument
            super.visitExec(tree);
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
        System.out.println("visitAssignop --- TODO");
        throw new UnsupportedOperationException();
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
        //TODO
    }

    @Override
    public void visitContinue(JCTree.JCContinue tree) {
        System.out.println("visitContinue");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitDoLoop(JCTree.JCDoWhileLoop tree) {
        super.visitDoLoop(tree);
        //TODO
        System.out.println("visitDoLoop --- TODO");
    }

    @Override
    public void visitErroneous(JCTree.JCErroneous tree) {
        System.out.println("visitErroneous");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) {
        super.visitForeachLoop(tree);
        System.out.println("visitForeachLoop --- TODO");
        //TODO
    }

    @Override
    public void visitForLoop(JCTree.JCForLoop tree) {
        throw new UnsupportedOperationException();
//        tree.init = translate(tree.init);
//        tree.cond = visitStatement(tree.cond);
//        tree.step = translate(tree.step);
//        tree.body = translate(tree.body);

    }

    @Override
    public void visitIf(JCTree.JCIf tree) {
        throw new UnsupportedOperationException();
//        tree.cond = visitStatement(tree.cond);
//        tree.thenpart = translate(tree.thenpart);
//
//        if (tree.elsepart instanceof JCTree.JCIf treeIf) {
//            visitIf(treeIf);
//
//
//        } else {
//            tree.elsepart = translate(tree.elsepart);
//
//        }
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
        System.out.println("visitLambda");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        StatementSequenceWithoutBlock builder = makeStatementSequence(tree.sym);
        Symbol.VarSymbol flowSymbol = builder.newSymbol(flowType);
        context.enterMethod(tree, flowSymbol);

        super.visitMethodDef(tree);

        JCTree.JCMethodDecl method = (JCTree.JCMethodDecl) this.result;

        String flowEvent = "flow";
        method.body = builder
                .executeAndBind(flowEvent, flowSymbol, (notUsed) -> logHelper.controlFlow())
                .execute((binds) -> logHelper.enter(binds.get(flowEvent), Flow))
                .block(method.body)
                .execute((binds) -> logHelper.exit(binds.get(flowEvent), Flow))
                .build();

        context.exitMethod();
    }

    @Override
    public void visitNewArray(JCTree.JCNewArray tree) {
        System.out.println("visitNewArray");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitNewClass(JCTree.JCNewClass tree) {
        super.visitNewClass(tree);
        JCTree.JCNewClass newClass = (JCTree.JCNewClass) this.result;

        String callName = "call";

        List<String> argsName = List.from(IntStream.range(0, newClass.args.size()).mapToObj(i -> "arg" + i).toList());
        ExpressionSequenceWithoutReturn builder = makeExpressionSequence();

        for (int i = 0; i < newClass.args.size(); ++i) {
            int finalI = i;
            builder = builder.executeAndBind(
                    argsName.get(i),
                    (notUsed) -> newClass.args.get(finalI),
                    newClass.constructorType.getParameterTypes().get(i));
        }
        this.result = builder.executeAndBind(callName, (notUsed) -> logHelper.call(), callType)
                .execute((binds) -> logHelper.logCall(
                        binds.get(callName),
                        argsName.map(name -> logHelper.valueRepr(mkTree.Ident(binds.get(name))))))
                .executeAndReturn(
                        "res",
                        (binds) -> {
                            newClass.args = argsName.map(name -> mkTree.Ident(binds.get(name)));
                            return newClass;
                        },
                        newClass.type
                )
                .execute((binds) -> logHelper.logReturn(binds.get(callName), logHelper.valueRepr(mkTree.Ident(binds.get("res")))))
                .build();

    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        System.out.println("visitReference");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitReturn(JCTree.JCReturn tree) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(tree);

        tree.expr = visitStatement(tree.expr);

        String statementEvent = "statementEvent";
        String subStatement = "subStatement";
        String result = "res";

        tree.expr = makeExpressionSequence()
                .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(), statementType)
                .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                .executeAndReturn("-", (noUsed) ->
                                makeExpressionSequence()
                                        .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(), subStatementType)
                                        .execute((binding) -> logHelper.enter(binding.get(subStatement), SubStatement))
                                        .executeAndReturn(result, (notUsed) -> tree.expr, tree.type)
                                        .execute((binding) -> logHelper.exit(binding.get(subStatement), SubStatement))
                                        .execute((binding) -> {
                                            Logger.Value value = logHelper.valueRepr(mkTree.Ident(binding.get(result)));
                                            return logHelper.logSimpleExpression(format, value, List.nil());
                                        })
                                        .build()
                        , tree.type)
                .execute((binds) -> logHelper.exit(binds.get(statementEvent), Statement))
                .execute((binds) -> logHelper.exit(context.currentMethod().methodEventGroup, Flow))
                .build();
    }

    @Override
    public void visitSkip(JCTree.JCSkip tree) {
        System.out.println("visitSkip");
        throw new UnsupportedOperationException();
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
                .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(), statementType)
                .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(), subStatementType)
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
        Symbol.VarSymbol tryClass = new Symbol.VarSymbol(0, helper.name("----try"), tryType, context.currentMethod().method.sym);

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
    public void visitTypeBoundKind(JCTree.TypeBoundKind tree) {
        System.out.println("visitTypeBoundKind");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeCast(JCTree.JCTypeCast tree) {
        System.out.println("visitTypeCast");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeIntersection(JCTree.JCTypeIntersection tree) {
        System.out.println("visitTypeIntersection");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeTest(JCTree.JCInstanceOf tree) {
        System.out.println("visitTypeTest");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeUnion(JCTree.JCTypeUnion tree) {
        System.out.println("visitTypeUnion");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitUnary(JCTree.JCUnary tree) {
        super.visitUnary(tree);
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitBinary(JCTree.JCBinary tree) {
        super.visitBinary(tree);
        this.result = logExecutionStep((JCTree.JCBinary) this.result);
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(tree);
        super.visitVarDef(tree);
        //we are in a method parameter
        if (tree.init != null) {
            JCTree.JCVariableDecl res = (JCTree.JCVariableDecl) this.result;

            String statementEvent = "statementEvent";
            String subStatement = "subStatement";
            String result = "res";

            tree.init = makeExpressionSequence()
                    .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(), statementType)
                    .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                    .executeAndReturn("-", (noUsed) ->
                                    makeExpressionSequence()
                                            .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(), subStatementType)
                                            .execute((binding) -> logHelper.enter(binding.get(subStatement), SubStatement))
                                            .executeAndReturn(result, (notUsed) -> res.init, res.type)
                                            .execute((binding) -> logHelper.exit(binding.get(subStatement), SubStatement))
                                            .execute((binding) -> {
                                                Logger.Identifier identifier = logHelper.localIdentifier("-", res.name.toString());
                                                Logger.Value value = logHelper.valueRepr(mkTree.Ident(binding.get(result)));
                                                Logger.Write write = logHelper.write(identifier, value);
                                                return logHelper.logSimpleExpression(format, value, List.of(write));
                                            })
                                            .build()
                            , tree.type)
                    .execute((binds) -> logHelper.exit(binds.get(statementEvent), Statement))
                    .build();
        }
    }

    @Override
    public void visitWhileLoop(JCTree.JCWhileLoop tree) {
        throw new UnsupportedOperationException();
//        tree.cond = visitStatement(tree.cond);
//        assertBlock(tree.body);
//        tree.body = translate(tree.body);


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

                throw new UnsupportedOperationException("unsupported");
            }
            case JCTree.JCNewClass call -> {
                super.visitNewClass(call);
                throw new UnsupportedOperationException("unsupported");
            }
            case JCTree.JCAssignOp assign -> {
                super.visitAssignop(assign);
                throw new UnsupportedOperationException("unsupported");
            }
            case JCTree.JCIdent ident -> {
                super.visitIdent(ident);
                JCTree.JCIdent res = (JCTree.JCIdent) this.result;

                String statementEvent = "statementEvent";


                yield makeExpressionSequence()
                        .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(), statementType)
                        .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                        .executeAndReturn("-", (binds) -> {
                            //TODO log execution step
                            return res;
                        }, res.type)
                        .execute((binds) -> logHelper.exit(binds.get(statementEvent), Statement))
                        .build();
            }
            case JCTree.JCUnary unary -> {
                //TODO extract boilerplate to generate statement and substatement event
                super.visitUnary(unary);
                JCTree.JCUnary res = (JCTree.JCUnary) this.result;

                String statementEvent = "statementEvent";
                String subStatement = "subStatement";

                yield makeExpressionSequence()
                        .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(), statementType)
                        .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                        .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(), subStatementType)
                        .executeAndReturn("-", (binds) -> {
                            res.arg = makeExpressionSequence()
                                    .execute((notUsed) -> logHelper.enter(binds.get(subStatement), SubStatement))
                                    .executeAndReturn("_", (notUsed) -> res.arg, res.type)
                                    .execute((notUsed) -> logHelper.exit(binds.get(subStatement), SubStatement))
                                    .build();
                            //TODO log execution step
                            return res;
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
                        .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(), statementType)
                        .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                        .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(), subStatementType)
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

                String statementEvent = "statementEvent";
                String subStatement = "subStatement";

                yield makeExpressionSequence()
                        .executeAndBind(statementEvent, (notUsed) -> logHelper.statment(), statementType)
                        .execute((binds) -> logHelper.enter(binds.get(statementEvent), Statement))
                        .executeAndBind(subStatement, (notUsed) -> logHelper.subStatment(), subStatementType)
                        .executeAndReturn("-", (binds) -> {
                            res.lhs = enterSubstatement(res.lhs, binds.get(subStatement));
                            res.rhs = exitSubstatement(res.rhs, binds.get(subStatement));
                            return logExecutionStep(res);
                        }, res.type)
                        .execute((binds) -> logHelper.exit(binds.get(statementEvent), Statement))
                        .build();

            }
            case JCTree.JCParens parens -> {
                super.visitParens(parens);
                throw new UnsupportedOperationException("unsupported");
            }
            default -> throw new IllegalStateException("Unexpected value: " + statement);
        };
    }

    /*******************************************************
     **************** log execution step  ******************
     *******************************************************/

    private JCTree.JCExpression logExecutionStep(JCTree.JCBinary binary) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(binary);

        String result = "result";
        return makeExpressionSequence()
                .executeAndReturn(result, (notUsed) -> binary, binary.type)
                .execute((binds) ->
                        logHelper.logSimpleExpression(
                                format,
                                logHelper.valueRepr(mkTree.Ident(binds.get(result))),
                                List.nil()))
                .build();
    }

    private JCTree.JCExpression logExecutionStep(JCTree.JCAssign assign) {
        SourceFormat.NodeSourceFormat format = makeNodeId.nodeId(assign);
        String result = "result";

        return makeExpressionSequence()
                .executeAndReturn(result, (notUsed) -> assign, assign.type)
                .execute((binds) -> {
                    //TODO compute value only 1 time
                    Logger.Identifier identifier = logHelper.localIdentifier("-", assign.lhs.toString());
                    Logger.Value value = logHelper.valueRepr(mkTree.Ident(binds.get(result)));
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
    private JCTree.JCExpression exitSubstatement(JCTree.JCExpression expr, Symbol event) {
        return makeExpressionSequence()
                .executeAndReturn("notUsed", (notUsed) -> (JCTree.JCExpression) expr, expr.type)
                .execute((notUsed) -> logHelper.exit(event, Logger.FileLoggerSubClasses.SubStatment.clazz))
                .build();
    }

    private JCTree.JCExpression enterSubstatement(JCTree.JCExpression expr, Symbol event) {
        return makeExpressionSequence()
                .execute((notUsed) -> logHelper.enter(event, Logger.FileLoggerSubClasses.SubStatment.clazz))
                .executeAndReturn("notUsed", (notUsed) -> (JCTree.JCExpression) expr, expr.type)
                .build();
    }

    /*******************************************************
     **************** make tree sequences ******************
     *******************************************************/

    /**************
     ********* constructors
     **************/

    public StatementSequenceWithoutBlock makeStatementSequence() {
        return StatementSequenceBuilder.make(context.currentMethod().method.sym, helper, mkTree);
    }

    public StatementSequenceWithoutBlock makeStatementSequence(Symbol.MethodSymbol symbol) {
        return StatementSequenceBuilder.make(symbol, helper, mkTree);
    }

    public ExpressionSequenceWithoutReturn makeExpressionSequence() {
        return new ExpressionSequenceBuilder(context.currentMethod().method.sym, helper, mkTree);
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
        Symbol.VarSymbol newSymbol(Type type);

        StatementSequenceWithoutBlock execute(Function<Map<String, Symbol>, JCTree.JCExpression> stat);

        StatementSequenceWithBlock block(JCTree.JCBlock block);

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
            public Symbol.VarSymbol newSymbol(Type type) {
                return nextSymbol(type);
            }

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
