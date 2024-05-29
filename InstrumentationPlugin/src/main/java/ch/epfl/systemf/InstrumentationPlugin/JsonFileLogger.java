package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Pair;
import org.w3c.dom.Node;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class JsonFileLogger implements TraceLogger {
    private final TreeHelper helper;
    private final Logger callLogger;
    private final TreeMaker mkTree;
    private final SourceFormat makeNodeId;


    public JsonFileLogger(TreeHelper instr, SourceFormat makeNodeId) {
        this.helper = instr;
        this.mkTree = instr.mkTree;
        this.makeNodeId = makeNodeId;

        TreeHelper.SimpleClass printLogger = new TreeHelper.SimpleClass("ch.epfl.systemf", "PrintLogger");
        callLogger = new Logger(helper);

    }

    /*******************************************************
     **************** Trace Logger Api ******************
     *******************************************************/

    /**************
     ********* Flow
     **************/

    @Override
    public JCTree.JCStatement logForLoop(JCTree.JCForLoop loop, Symbol.MethodSymbol currMethod) {
        SourceFormat.NodeSourceFormat loopId = makeNodeId.nodeId(loop.body);

        loop.body = enterExitFlow(loop.body, loopId);
        return loop;
    }

    @Override
    public JCTree.JCStatement logWhileLoop(JCTree.JCWhileLoop loop, Symbol.MethodSymbol currMethod) {
        loop.body = enterExitFlow(loop.body, makeNodeId.nodeId(loop.body));
        return loop;
    }

    @Override
    public JCTree.JCStatement logIf(JCTree.JCIf branch, Symbol.MethodSymbol currMethod) {
        JCTree.JCStatement then = branch.thenpart;
        JCTree.JCStatement elsePart = branch.elsepart;
        if (then != null) {
            branch.thenpart = enterExitFlow(then, makeNodeId.nodeId(branch.thenpart));
        }
        if (elsePart != null) {
            branch.elsepart = enterExitFlow(elsePart, makeNodeId.nodeId(branch.elsepart));
        }
        return branch;
    }

    @Override
    public JCTree.JCStatement logIfElse(JCTree.JCIf branch, Symbol.MethodSymbol currMethod) {
        JCTree.JCStatement then = branch.thenpart;
        if (then != null) {
            branch.thenpart = enterExitFlow(then, makeNodeId.nodeId(branch.thenpart));
        }

        return branch;
    }

    @Override
    public JCTree.JCMethodDecl logMethod(JCTree.JCMethodDecl method, Symbol.MethodSymbol currMethod) {
        JCTree.JCBlock body = method.getBody();

        body.stats = enterExitFlow(body.getStatements(), makeNodeId.nodeId(method.body));
        return method;
    }

    @Override
    public JCTree.JCStatement logReturn(JCTree.JCReturn ret, Symbol.MethodSymbol currMethod) {

        ret.expr = exitFlow(ret.expr,
                currMethod.getReturnType(),
                makeNodeId.nodeId(ret),
                currMethod);
        return ret;
    }


    /**************
     ********* Statement
     **************/


    @Override
    public JCTree.JCStatement logVarDecl(JCTree.JCVariableDecl varDecl, Symbol.MethodSymbol currMethod) {
        if (varDecl.init == null)
            throw new IllegalArgumentException();

        varDecl.init = logUpdate(
                varDecl.init,
                varDecl.type,
                makeNodeId.nodeId(varDecl),
                new Logger.LocalIdentifier(varDecl.name.toString()),
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
        if (call.type.equals(helper.voidP))
            throw new IllegalArgumentException();

        List<Symbol.VarSymbol> symbols = argsSymbols(call, currMethod);
        Symbol.VarSymbol notUsed = new Symbol.VarSymbol(0, helper.name("+++"), helper.intP, currMethod);

        SourceFormat.NodeSourceFormat id = makeNodeId.nodeId(call);

        JCTree.JCExpression logArgsAndCall = mkTree.LetExpr(
                storeArgValues(symbols, call.args).append(mkTree.VarDef(notUsed, callLogger.resultCall.callStatic(id.identifier(), "", symbols))),
                call
        ).setType(call.type);
        call.args = symbols.map(mkTree::Ident);

        return applyBeforeAfter(logArgsAndCall,
                call.type,
                () -> callLogger.resultCall.enter(id.identifier()),
                s -> callLogger.resultCall.exit(id.identifier(), s),
                currMethod);
    }

    @Override
    public JCTree.JCStatement logVoidCallStatement(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod) {
        if (!call.type.equals(helper.voidP))
            throw new IllegalArgumentException();

        SourceFormat.NodeSourceFormat id = makeNodeId.nodeId(call);



        List<Symbol.VarSymbol> symbols = argsSymbols(call, currMethod);
        JCTree.JCStatement logArgsAndCall = mkTree.Block(0,
                storeArgValues(symbols, call.args)
                        .append(mkTree.Exec(callLogger.voidCall.callStatic(id.identifier(), "", symbols)))
                        .append(mkTree.Exec(call))
        );
        call.args = symbols.map(mkTree::Ident);

        return applyBeforeAfter(logArgsAndCall,
                () -> callLogger.voidCall.enter(id.identifier()),
                () -> callLogger.voidCall.exit(id.identifier())
        );
    }

    private List<JCTree.JCStatement> storeArgValues(List<Symbol.VarSymbol> symbols, List<JCTree.JCExpression> args){
       return List.from(
               IntStream.range(0, symbols.size())
                       .mapToObj(i -> mkTree.VarDef(symbols.get(i), args.get(i)))
                       .toList());
    }

    private List<Symbol.VarSymbol> argsSymbols(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod){
        return argsSymbols(call.getMethodSelect().type.getParameterTypes(), currMethod);
    }

    private List<Symbol.VarSymbol> argsSymbols(List<Type> argTypes, Symbol.MethodSymbol currMethod){
        return List.from(IntStream.range(0, argTypes.size())
                .mapToObj(i -> new Symbol.VarSymbol(0, helper.name("**arg" + i), argTypes.get(i), currMethod))
                .toList()
        );
    }

    //we can factorize a lot of methods in statements and expression as most do the same thing, but with different labels
    @Override
    public JCTree.JCExpression logUnaryStatement(JCTree.JCUnary unary, Symbol.MethodSymbol currMethod) {
        String name = unary.getExpression().toString();

        SourceFormat.NodeSourceFormat id = makeNodeId.nodeId(unary);

        JCTree.JCExpression withUpdate = extractIdentifier(
                (result) -> {
                    unary.arg = result.snd;
                    return logUpdate(unary, unary.type, id, result.fst, currMethod);
                },
                unary.getExpression(),
                unary.type,
                currMethod
        );
        return enterExitStatement(withUpdate, unary.type, id, currMethod);
    }

    @Override
    public JCTree.JCExpression logAssignStatement(JCTree.JCAssign assign, Symbol.MethodSymbol currentMethod) {
        SourceFormat.NodeSourceFormat id = makeNodeId.nodeId(assign);
        JCTree.JCExpression tmp = extractIdentifier(
                (result) -> {
                    assign.lhs = result.snd;
                    return assignHelper(assign, id, result.fst, currentMethod);
                },
                assign.lhs,
                assign.type,
                currentMethod
        );
        return tmp;
    }

    @Override
    public JCTree.JCExpression logAssignOpStatement(JCTree.JCAssignOp assign, Symbol.MethodSymbol currMethod) {
        SourceFormat.NodeSourceFormat id = makeNodeId.nodeId(assign);
        return extractIdentifier(
                (result) -> {
                    assign.lhs = result.snd;
                    return assignHelper(assign, id, result.fst, currMethod);
                },
                assign.lhs,
                assign.type,
                currMethod
        );
    }

    //it must wrap around a log function => context must return an int
    private JCTree.JCExpression extractIdentifier(Function<Pair<Logger.Identifier, JCTree.JCExpression>, JCTree.JCExpression> context, JCTree.JCExpression identifier, Type ret, Symbol currMethod){
        switch (identifier){
            case JCTree.JCIdent ident:
                return context.apply(new Pair<>(new Logger.LocalIdentifier(ident.toString()), ident));
            case JCTree.JCFieldAccess select: {
                Symbol.VarSymbol ref = new Symbol.VarSymbol(0, helper.name("&&&"), select.selected.type, currMethod);
                JCTree.JCExpression selected = select.selected;
                select.selected = mkTree.Ident(ref);

                return mkTree.LetExpr(mkTree.VarDef(ref, selected),
                        context.apply(new Pair<>(new Logger.FieldIdentifier(ref, select.name.toString()), select))
                ).setType(ret);
            }
            default: throw new UnsupportedOperationException();
        }
    }

    private JCTree.JCExpression assignHelper(JCTree.JCExpression assign, SourceFormat.NodeSourceFormat id, Logger.Identifier identifier, Symbol.MethodSymbol currMethod){

        JCTree.JCExpression withUpdate = logUpdate(
                assign,
                assign.type,
                id,
                identifier,
                currMethod
        );

        return enterExitStatement(
                withUpdate,
                assign.type,
                id,
                currMethod);
    }

    @Override
    public JCTree.JCExpression logStatement(JCTree.JCExpression statement, Symbol.MethodSymbol currentMethod) {
        return enterExitStatement(statement, statement.type, makeNodeId.nodeId(statement), currentMethod);
    }

    @Override
    public JCTree.JCExpression logNewClassStatement(JCTree.JCNewClass call, Symbol.MethodSymbol currMethod) {

        List<Symbol.VarSymbol> symbols = argsSymbols(call.constructorType.getParameterTypes(), currMethod);
        Symbol.VarSymbol logMethod = new Symbol.VarSymbol(0, helper.name("+++"), helper.intP, currMethod);

        SourceFormat.NodeSourceFormat id = makeNodeId.nodeId(call);

        JCTree.JCExpression logArgsAndCall = mkTree.LetExpr(
                storeArgValues(symbols, call.args).append(mkTree.VarDef(logMethod, callLogger.resultCall.callStatic(id.identifier(), "", symbols))),
                call
        ).setType(call.type);
        call.args = symbols.map(mkTree::Ident);

        return applyBeforeAfter(logArgsAndCall,
                call.type,
                () -> callLogger.resultCall.enter(id.identifier()),
                s -> callLogger.resultCall.exit(id.identifier(), s),
                currMethod);
    }



    /**************
     ********* Expression
     **************/

    @Override
    public JCTree.JCExpression logUnaryExpr(JCTree.JCUnary unary, Symbol.MethodSymbol currMethod) {
        String name = unary.getExpression().toString();

        SourceFormat.NodeSourceFormat id = makeNodeId.nodeId(unary);
        //nearly the same code as statement we should factor it, if one change likely the other
        JCTree.JCExpression withUpdate = extractIdentifier(
                (result) -> {
                    unary.arg = result.snd;
                    return logUpdate(unary, unary.type, id, result.fst, currMethod);
                },
                unary.getExpression(),
                unary.type,
                currMethod
        );

        return enterExitExpression(withUpdate,
                unary.type,
                id,
                currMethod);
    }

    @Override
    public JCTree.JCExpression logExpression(JCTree.JCExpression statement, Symbol.MethodSymbol currentMethod) {
        SourceFormat.NodeSourceFormat id = makeNodeId.nodeId(statement);
        return enterExitExpression(statement, statement.type, id, currentMethod);
    }

    /*******************************************************
     **************** Helpers ******************
     *******************************************************/


    /**************
     ********* Inject Flow
     **************/

    private List<JCTree.JCStatement> enterExitFlow(List<JCTree.JCStatement> stats, SourceFormat.NodeSourceFormat id) {
        return applyBeforeAfter(stats,
                () -> callLogger.simpleFlow.enter(id.identifier()),
                () -> callLogger.simpleFlow.exit(id.identifier()));
    }

    private JCTree.JCStatement enterExitFlow(JCTree.JCStatement stat, SourceFormat.NodeSourceFormat id) {
        return applyBeforeAfter(stat,
                () -> callLogger.simpleFlow.enter(id.identifier()),
                () -> callLogger.simpleFlow.exit(id.identifier()));
    }

    private JCTree.JCStatement enterFlow(JCTree.JCStatement stat, SourceFormat.NodeSourceFormat id) {
        return applyBefore(stat,
                () -> callLogger.simpleFlow.enter(id.identifier()));
    }


    private JCTree.JCExpression exitFlow(JCTree.JCExpression expr, Type exprType, SourceFormat.NodeSourceFormat id, Symbol.MethodSymbol method) {
        return applyAfter(expr, exprType, symbol -> callLogger.simpleFlow.exit(id.identifier()), method);
    }

    /**************
     ********* Inject Statement label
     **************/


    private JCTree.JCExpression enterExitStatement(JCTree.JCExpression expr,
                                                   Type exprType,
                                                   SourceFormat.NodeSourceFormat id,
                                                   Symbol.MethodSymbol method) {

        return applyBeforeAfter(expr,
                exprType,
                () -> callLogger.simpleStatement.enter(id.identifier()),
                symbol -> callLogger.simpleStatement.exit(id.identifier(), symbol),
                method);
    }


    /**************
     ********* Inject expression label
     **************/

    private JCTree.JCExpression enterExitExpression(JCTree.JCExpression expr,
                                                    Type exprType,
                                                    SourceFormat.NodeSourceFormat id,
                                                    Symbol.MethodSymbol method) {

        return applyBeforeAfter(expr,
                exprType,
                () -> callLogger.simpleExpression.enter(id.identifier()),
                symbol -> callLogger.simpleExpression.exit(id.identifier(), symbol),
                method);
    }

    /**************
     ********* Inject update label
     **************/

    private JCTree.JCExpression logUpdate(JCTree.JCExpression expr,
                                          Type exprType,
                                          SourceFormat.NodeSourceFormat id,
                                          Logger.Identifier identifier,
                                          Symbol.MethodSymbol method) {

        return applyAfter(expr,
                exprType,
                symbol -> callLogger.update.write(id.identifier(), identifier, symbol),
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

        return switch (stat) {
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

        return switch (stat) {
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
        if (exprType.equals(helper.voidP))
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
        if (exprType.equals(helper.voidP))
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
