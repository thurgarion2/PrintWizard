package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

import java.util.function.Function;
import java.util.function.Supplier;

public class JsonFileLogger implements TraceLogger {
    private final TreeHelper helper;
    private final Logger callLogger;
    private final TreeMaker mkTree;
    private final NodeIdFactory makeNodeId;


    public JsonFileLogger(TreeHelper instr, NodeIdFactory makeNodeId) {
        this.helper = instr;
        this.mkTree = instr.mkTree;
        this.makeNodeId = makeNodeId;

        TreeHelper.SimpleClass printLogger = new TreeHelper.SimpleClass("ch.epfl.systemf", "PrintLogger");
        callLogger = new Logger(printLogger, helper);

    }

    /*******************************************************
     **************** Trace Logger Api ******************
     *******************************************************/

    /**************
     ********* Flow
     **************/

    @Override
    public JCTree.JCStatement logForLoop(JCTree.JCForLoop loop, Symbol.MethodSymbol currMethod) {
        NodeIdFactory.NodeId loopId = makeNodeId.mutipleLineNode;

        loop.body = enterFlow(loop.body, loopId);
        loop.step = loop.step.append(mkTree.Exec(callLogger.exitFlow(loopId.identifier())));
        return enterExitFlow(loop, makeNodeId.mutipleLineNode);
    }

    @Override
    public JCTree.JCStatement logWhileLoop(JCTree.JCWhileLoop loop, Symbol.MethodSymbol currMethod) {
        loop.body = enterExitFlow(loop.body, makeNodeId.mutipleLineNode);
        return enterExitFlow(loop, makeNodeId.mutipleLineNode);
    }

    @Override
    public JCTree.JCStatement logIf(JCTree.JCIf branch, Symbol.MethodSymbol currMethod) {
        JCTree.JCStatement then = branch.thenpart;
        JCTree.JCStatement elsePart = branch.elsepart;
        if (then != null) {
            branch.thenpart = enterExitFlow(then, makeNodeId.mutipleLineNode);
        }
        if (elsePart != null) {
            branch.elsepart = enterExitFlow(elsePart, makeNodeId.mutipleLineNode);
        }
        return enterExitFlow(branch, makeNodeId.mutipleLineNode);
    }

    @Override
    public JCTree.JCStatement logIfElse(JCTree.JCIf branch, Symbol.MethodSymbol currMethod) {
        JCTree.JCStatement then = branch.thenpart;
        if (then != null) {
            branch.thenpart = enterExitFlow(then, makeNodeId.mutipleLineNode);
        }

        return branch;
    }

    @Override
    public JCTree.JCMethodDecl logMethod(JCTree.JCMethodDecl method, Symbol.MethodSymbol currMethod) {
        JCTree.JCBlock body = method.getBody();

        body.stats = enterExitFlow(body.getStatements(), makeNodeId.mutipleLineNode);
        return method;
    }


    /**************
     ********* Statement
     **************/

    @Override
    public JCTree.JCStatement logReturn(JCTree.JCReturn ret, Symbol.MethodSymbol currMethod) {

        ret.expr = exitFlow(ret.expr,
                currMethod.getReturnType(),
                makeNodeId.mutipleLineNode,
                currMethod);
        return ret;
    }

    @Override
    public JCTree.JCStatement logVarDecl(JCTree.JCVariableDecl varDecl, Symbol.MethodSymbol currMethod) {
        if (varDecl.init == null)
            throw new IllegalArgumentException();

        varDecl.init = logUpdate(
                varDecl.init,
                varDecl.type,
                makeNodeId.nodeId(varDecl),
                varDecl.name.toString(),
                currMethod
        );

        varDecl.init = enterExitStatement(
                varDecl.init,
                varDecl.type,
                makeNodeId.nodeId(varDecl),
                currMethod);
        return varDecl;
    }

    @Override
    public JCTree.JCExpression logCallStatement(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod) {
        if(call.type.equals(helper.voidP))
            throw new IllegalArgumentException();
        return enterExitStatement(call, call.type, makeNodeId.nodeId(call), currMethod);
    }

    @Override
    public JCTree.JCStatement logVoidCallStatement(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod) {
        if(!call.type.equals(helper.voidP))
            throw new IllegalArgumentException();

        NodeIdFactory.NodeId id = makeNodeId.nodeId(call);

        return applyBeforeAfter(mkTree.Exec(call),
                () -> callLogger.enterStatement(id.identifier()),
                () -> callLogger.exitStatement(id.identifier())
        );
    }

    //we can factorize a lot of methods in statements and expression as most do the same thing, but with different labels
    @Override
    public JCTree.JCExpression logUnaryStatement(JCTree.JCUnary unary, Symbol.MethodSymbol currMethod) {
        String name = unary.getExpression().toString();

        NodeIdFactory.NodeId id = makeNodeId.nodeId(unary);

        JCTree.JCExpression withUpdate = logUpdate(unary, unary.type, id, name, currMethod);
        return enterExitStatement(withUpdate, unary.type, id, currMethod);
    }

    @Override
    public JCTree.JCExpression logAssignStatement(JCTree.JCAssign result, Symbol.MethodSymbol currentMethod) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JCTree.JCExpression logStatement(JCTree.JCExpression statement, Symbol.MethodSymbol currentMethod) {
        return enterExitStatement(statement, statement.type, makeNodeId.nodeId(statement), currentMethod);
    }


    /**************
     ********* Expression
     **************/


    @Override
    public JCTree.JCExpression logCallExpr(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod) {
        if (call.type.equals(helper.voidP))
            throw new IllegalArgumentException("cannot assign void to variable");
        return enterExitExpression(call, call.type, makeNodeId.nodeId(call), currMethod);
    }

    @Override
    public JCTree.JCExpression logUnaryExpr(JCTree.JCUnary unary, Symbol.MethodSymbol currMethod) {
        String name = unary.getExpression().toString();

        NodeIdFactory.NodeId id = makeNodeId.nodeId(unary);
        JCTree.JCExpression withUpdate = logUpdate(unary, unary.type, id, name, currMethod);

        return enterExitExpression(withUpdate,
                unary.type,
                id,
                currMethod);
    }

    /*******************************************************
     **************** Helpers ******************
     *******************************************************/


    /**************
     ********* Inject Flow
     **************/

    private List<JCTree.JCStatement> enterExitFlow(List<JCTree.JCStatement> stats, NodeIdFactory.NodeId id) {
        return applyBeforeAfter(stats,
                () -> callLogger.enterFlow(id.identifier()),
                () -> callLogger.exitFlow(id.identifier()));
    }

    private JCTree.JCStatement enterExitFlow(JCTree.JCStatement stat, NodeIdFactory.NodeId id) {
        return applyBeforeAfter(stat,
                () -> callLogger.enterFlow(id.identifier()),
                () -> callLogger.exitFlow(id.identifier()));
    }

    private JCTree.JCStatement enterFlow(JCTree.JCStatement stat, NodeIdFactory.NodeId id) {
        return applyBefore(stat,
                () -> callLogger.enterFlow(id.identifier()));
    }


    private JCTree.JCExpression exitFlow(JCTree.JCExpression expr, Type exprType, NodeIdFactory.NodeId id, Symbol.MethodSymbol method) {
        return applyAfter(expr, exprType, symbol -> callLogger.exitFlow(id.identifier()), method);
    }

    /**************
     ********* Inject Statement label
     **************/


    private JCTree.JCExpression enterExitStatement(JCTree.JCExpression expr,
                                                    Type exprType,
                                                    NodeIdFactory.NodeId id,
                                                    Symbol.MethodSymbol method) {

        return applyBeforeAfter(expr,
                exprType,
                () -> callLogger.enterStatement(id.identifier()),
                symbol -> callLogger.exitStatement(id.identifier()),
                method);
    }



    /**************
     ********* Inject expression label
     **************/

    private JCTree.JCExpression enterExitExpression(JCTree.JCExpression expr,
                                                    Type exprType,
                                                    NodeIdFactory.NodeId id,
                                                    Symbol.MethodSymbol method) {
        return applyBeforeAfter(expr,
                exprType,
                () -> callLogger.enterExpression(id.identifier()),
                symbol -> callLogger.exitExpression(id.identifier(), symbol),
                method);
    }

    /**************
     ********* Inject update label
     **************/

    private JCTree.JCExpression logUpdate(JCTree.JCExpression expr,
                                          Type exprType,
                                          NodeIdFactory.NodeId id,
                                          String name,
                                          Symbol.MethodSymbol method) {
        return applyAfter(expr,
                exprType,
                symbol -> callLogger.update(id.identifier(), name, symbol),
                method);
    }

    /**************
     ********* Inject method calls before/after an instruction
     **************/

    /********
     **** Inject before/after JCTree.Statement
     ********/

    private JCTree.JCStatement applyBefore(JCTree.JCStatement stat,
                                                Supplier<JCTree.JCExpression> before) {

        return switch (stat){
            case JCTree.JCBlock block -> {
                block.stats = block.stats.prepend(mkTree.Exec(before.get()));
                yield block;
            }
            default -> {
                yield mkTree.Block(0, List.of(
                        mkTree.Exec(before.get()),
                        stat
                ));
            }
        };
    }

    private JCTree.JCStatement applyBeforeAfter(JCTree.JCStatement stat,
                                                Supplier<JCTree.JCExpression> before,
                                                Supplier<JCTree.JCExpression> after) {

        return switch (stat){
            case JCTree.JCBlock block -> {
                block.stats = applyBeforeAfter(block.stats, before, after);
                yield block;
            }
            default -> {
                yield mkTree.Block(0, List.of(
                        mkTree.Exec(before.get()),
                        stat,
                        mkTree.Exec(after.get())
                ));
            }
        };
    }

    /********
     **** Inject before/after JCTree.Statement
     ********/

    private List<JCTree.JCStatement> applyBeforeAfter(List<JCTree.JCStatement> stats,
                                                Supplier<JCTree.JCExpression> before,
                                                Supplier<JCTree.JCExpression> after) {

        return stats.prepend(mkTree.Exec(before.get())).append(mkTree.Exec(after.get()));
    }

    /********
     **** Inject before/after JCTree.JCExpression
     ********/

    private JCTree.JCExpression applyAfter(JCTree.JCExpression expr,
                                           Type exprType,
                                           Function<Symbol, JCTree.JCExpression> after,
                                           Symbol.MethodSymbol method) {
        if(exprType.equals(helper.voidP))
            throw new IllegalArgumentException("void type cannot be bind to variable");

        Symbol.VarSymbol exit = new Symbol.VarSymbol(0,
                helper.name("----"),
                helper.intP,
                method);
        Symbol.VarSymbol retValue = new Symbol.VarSymbol(0,
                helper.name("retValue"),
                exprType,
                method);

        return mkTree.LetExpr(
                List.of(mkTree.VarDef(retValue, expr),
                        mkTree.VarDef(exit, after.apply(retValue))
                ),
                mkTree.Ident(retValue)
        ).setType(exprType);

    }

    private JCTree.JCExpression applyBeforeAfter(JCTree.JCExpression expr,
                                                 Type exprType,
                                                 Supplier<JCTree.JCExpression> before,
                                                 Function<Symbol, JCTree.JCExpression> after,
                                                 Symbol.MethodSymbol method) {
        if(exprType.equals(helper.voidP))
            throw new IllegalArgumentException("void type cannot be bind to variable");

        Symbol.VarSymbol enter = new Symbol.VarSymbol(0,
                helper.name("****"),
                helper.intP,
                method);
        Symbol.VarSymbol exit = new Symbol.VarSymbol(0,
                helper.name("----"),
                helper.intP,
                method);
        Symbol.VarSymbol retValue = new Symbol.VarSymbol(0,
                helper.name("+++++"),
                exprType,
                method);


        return mkTree.LetExpr(
                List.of(
                        mkTree.VarDef(enter, before.get()),
                        mkTree.VarDef(retValue, expr),
                        mkTree.VarDef(exit, after.apply(retValue))
                ),
                mkTree.Ident(retValue)
        ).setType(expr.type);
    }
}
