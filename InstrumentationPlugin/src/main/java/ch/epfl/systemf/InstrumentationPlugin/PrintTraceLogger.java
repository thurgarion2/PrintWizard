package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

import java.util.function.Function;
import java.util.function.Supplier;

public class PrintTraceLogger implements TraceLogger {
    private final TreeHelper helper;
    private final Logger callLogger;
    private final TreeMaker mkTree;


    public PrintTraceLogger(TreeHelper instr) {
        this.helper = instr;
        this.mkTree = instr.mkTree;

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
    public JCTree.JCStatement logForLoop(NodeId forLoop, NodeId init, NodeId iter, NodeId cond, JCTree.JCForLoop loop, Symbol.MethodSymbol currMethod) {
        loop.body = enterExitFlow(loop.body, iter);
        loop.cond = enterExitStatement(loop.cond, helper.boolP, cond, currMethod);
        return enterExitFlow(loop, forLoop);
    }

    @Override
    public JCTree.JCStatement logWhileLoop(NodeId whileLoop, NodeId iter, NodeId cond, JCTree.JCWhileLoop loop, Symbol.MethodSymbol currMethod) {
        loop.body = enterExitFlow(loop.body, iter);
        loop.cond = enterExitStatement(loop.cond, helper.boolP, cond, currMethod);
        return enterExitFlow(loop, whileLoop);
    }

    @Override
    public JCTree.JCStatement logIf(NodeId iF, NodeId cond, NodeId theN, NodeId elsE, JCTree.JCIf branch, Symbol.MethodSymbol currMethod) {
        JCTree.JCStatement then = branch.thenpart;
        JCTree.JCStatement elsePart = branch.elsepart;
        if (then != null) {
            branch.thenpart = enterExitFlow(then, theN);
        }
        if (elsePart != null) {
            branch.elsepart = enterExitFlow(elsePart, elsE);
        }

        branch.cond = enterExitStatement(branch.cond, helper.boolP, cond, currMethod);

        return enterExitFlow(branch, iF);
    }

    @Override
    public JCTree.JCMethodDecl logMethod(NodeId nodeId, JCTree.JCMethodDecl method, Symbol.MethodSymbol currMethod) {
        String methodName = method.name.toString();
        JCTree.JCBlock body = method.getBody();

        body.stats = enterExitFlow(body.getStatements(), nodeId);
        return method;
    }


    /**************
     ********* Statement
     **************/

    @Override
    public JCTree.JCStatement logReturn(NodeId nodeId, NodeId method, JCTree.JCReturn ret, Symbol.MethodSymbol currMethod) {

        ret.expr = enterExitStatement(ret.expr,
                currMethod.getReturnType(),
                nodeId,
                currMethod);

        ret.expr = exitFlow(ret.expr,
                currMethod.getReturnType(),
                method,
                currMethod);
        return ret;
    }

    @Override
    public JCTree.JCStatement logVarDecl(NodeId nodeId, JCTree.JCVariableDecl varDecl, Symbol.MethodSymbol currMethod) {
        if (varDecl.init == null)
            throw new IllegalArgumentException();

        varDecl.init = logUpdate(
                varDecl.init,
                varDecl.type,
                nodeId,
                varDecl.name.toString(),
                currMethod
        );

        varDecl.init = enterExitStatement(
                varDecl.init,
                varDecl.type,
                nodeId,
                currMethod);
        return varDecl;
    }

    @Override
    public JCTree.JCStatement logCallStatement(NodeId nodeId, JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod) {
        if(call.type.equals(helper.voidP)){

            return enterExitStatement(mkTree.Exec(call), nodeId);
        }else{
            return enterExitStatementStatRet(call, call.type, nodeId, currMethod);
        }
    }

    //we can factorize a lot of methods in statements and expression as most do the same thing, but with different labels
    @Override
    public JCTree.JCExpressionStatement logUnaryStatement(NodeId nodeId, JCTree.JCUnary unary, Symbol.MethodSymbol currMethod) {
        String name = unary.getExpression().toString();
        JCTree.JCExpression withUpdate = logUpdate(unary, unary.type, nodeId, name, currMethod);

        return enterExitStatementStatRet(withUpdate, unary.type, nodeId, currMethod);
    }

    @Override
    public JCTree.JCExpressionStatement logAssignStatement(NodeId id, JCTree.JCAssign result, Symbol.MethodSymbol currentMethod) {
        throw new UnsupportedOperationException();
    }


    /**************
     ********* Expression
     **************/


    @Override
    public JCTree.JCExpression logCallExpr(NodeId nodeId, JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod) {
        if (call.type.equals(helper.voidP))
            throw new IllegalArgumentException("cannot assign void to variable");
        return enterExitExpression(call, call.type, nodeId, currMethod);
    }

    @Override
    public JCTree.JCExpression logUnaryExpr(NodeId nodeId, JCTree.JCUnary unary, Symbol.MethodSymbol currMethod) {
        String name = unary.getExpression().toString();
        JCTree.JCExpression withUpdate = logUpdate(unary, unary.type, nodeId, name, currMethod);

        return enterExitExpression(withUpdate,
                unary.type,
                nodeId,
                currMethod);
    }

    /*******************************************************
     **************** Helpers ******************
     *******************************************************/


    /**************
     ********* Inject Flow
     **************/

    private List<JCTree.JCStatement> enterExitFlow(List<JCTree.JCStatement> stats, NodeId id) {
        return applyBeforeAfter(stats,
                () -> callLogger.enterFlow(id.nodeId()),
                () -> callLogger.exitFlow(id.nodeId()));
    }

    private JCTree.JCStatement enterExitFlow(JCTree.JCStatement stat, NodeId id) {
        return applyBeforeAfter(stat,
                () -> callLogger.enterFlow(id.nodeId()),
                () -> callLogger.exitFlow(id.nodeId()));
    }


    private JCTree.JCExpression exitFlow(JCTree.JCExpression expr, Type exprType, NodeId id, Symbol.MethodSymbol method) {
        return applyAfter(expr, exprType, symbol -> callLogger.exitFlow(id.nodeId()), method);
    }

    /**************
     ********* Inject Statement label
     **************/

    private JCTree.JCExpressionStatement enterExitStatementStatRet(JCTree.JCExpression expr,
                                                   Type exprType,
                                                   NodeId id,
                                                   Symbol.MethodSymbol method) {

        return mkTree.Exec(enterExitStatement(expr, exprType, id, method));
    }

    private JCTree.JCStatement enterExitStatement(JCTree.JCExpressionStatement stat,
                                                   NodeId id) {

        return applyBeforeAfter(stat,
                () -> callLogger.enterStatement(id.nodeId()),
                () -> callLogger.exitStatement(id.nodeId())
                );
    }

    private JCTree.JCExpression enterExitStatement(JCTree.JCExpression expr,
                                                    Type exprType,
                                                    NodeId id,
                                                    Symbol.MethodSymbol method) {

        return applyBeforeAfter(expr,
                exprType,
                () -> callLogger.enterStatement(id.nodeId()),
                symbol -> callLogger.exitStatement(id.nodeId()),
                method);
    }



    /**************
     ********* Inject expression label
     **************/

    private JCTree.JCExpression enterExitExpression(JCTree.JCExpression expr,
                                                    Type exprType,
                                                    NodeId id,
                                                    Symbol.MethodSymbol method) {
        return applyBeforeAfter(expr,
                exprType,
                () -> callLogger.enterExpression(id.nodeId()),
                symbol -> callLogger.exitExpression(id.nodeId(), symbol),
                method);
    }

    /**************
     ********* Inject update label
     **************/

    private JCTree.JCExpression logUpdate(JCTree.JCExpression expr,
                                          Type exprType,
                                          NodeId id,
                                          String name,
                                          Symbol.MethodSymbol method) {
        return applyAfter(expr,
                exprType,
                symbol -> callLogger.update(id.nodeId(), name, symbol),
                method);
    }

    /**************
     ********* Inject method calls before/after an instruction
     **************/

    /********
     **** Inject before/after JCTree.Statement
     ********/

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
