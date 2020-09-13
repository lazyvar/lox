package com.hasz.lang.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<String, Boolean>> scopes = new Stack<>();
  private FunctionType currentFunction = FunctionType.NONE;
  private ClassType currentClass = ClassType.NONE;

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  private enum FunctionType {
    NONE, FUNCTION, METHOD, INITIALIZER
  }

  private enum ClassType {
    NONE, CLASS, SUBCLASS
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    resolveLocal(expr, expr.name);

    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);

    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  @Override
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);

    return null;
  }

  @Override
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);

    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);

    return null;
  }

  @Override
  public Void visitThisExpr(Expr.This expr) {
    if (currentClass == ClassType.NONE) {
      Main.error(expr.keyword, "Cannot use 'this' outside of a class.");

      return null;
    }

    resolveLocal(expr, expr.keyword);

    return null;
  }

  @Override
  public Void visitSuperExpr(Expr.Super expr) {
    if (currentClass == ClassType.NONE) {
      Main.error(expr.keyword, "Cannot use 'super' outside of a class.");

      return null;
    }

    if (currentClass == ClassType.CLASS) {
      Main.error(expr.keyword, "Cannot use 'super' outside of a class.");

      return null;
    }

    resolveLocal(expr, expr.keyword);

    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);

    return null;
  }

  @Override
  public Void visitConditionalExpr(Expr.Conditional expr) {
    resolve(expr.condition);
    resolve(expr.ifBranch);
    resolve(expr.elseBranch);

    return null;
  }

  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
      Main.error(expr.name, "Cannot read local variable in its own initializer.");
    }

    resolveLocal(expr, expr.name);

    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);

    return null;
  }

  @Override
  public Void visitScopedBlockStmt(Stmt.ScopedBlock stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();

    return null;
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    resolve(stmt.statements);

    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    ClassType enclosingClass = currentClass;

    currentClass = ClassType.CLASS;
    declare(stmt.name);
    define(stmt.name);

    if (stmt.superclass != null) {
      currentClass = ClassType.SUBCLASS;

      if (stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
        Main.error(stmt.superclass.name, "A class cannot inherit from itself.");
      }

      resolve(stmt.superclass);
    }

    if (stmt.superclass != null) {
      beginScope();
      scopes.peek().put("super", true);
    }

    beginScope();
    scopes.peek().put("this", true);

    for (Stmt.Function method : stmt.methods) {
      resolveFunction(method, method.name.lexeme.equals("init") ? FunctionType.INITIALIZER : FunctionType.METHOD);
    }

    if (stmt.superclass != null) {
      endScope();
    }

    endScope();
    currentClass = enclosingClass;

    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);

    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);

    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
      Main.error(stmt.keyword, "Cannot return from top-level code.");
    }

    if (currentFunction == FunctionType.INITIALIZER) {
      Main.error(stmt.keyword, "Cannot return a value from an initializer.");
    }

    if (stmt.value != null) {
      resolve(stmt.value);
    }

    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(stmt, FunctionType.FUNCTION);

    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);

    if (stmt.elseBranch != null) {
      resolve(stmt.elseBranch);
    }

    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);

    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }

    define(stmt.name);

    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);

    return null;
  }

  @Override
  public Void visitForStmt(Stmt.For stmt) {
    if (stmt.initial != null) {
      resolve(stmt.initial);
    }

    if (stmt.condition != null) {
      resolve(stmt.condition);
    }

    if (stmt.body != null) {
      resolve(stmt.body);
    }

    if (stmt.increment != null) {
      resolve(stmt.increment);
    }

    return null;
  }


  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }

  private void resolve(Expr expr) {
    expr.accept(this);
  }

  private void beginScope() {
    scopes.push(new HashMap<String, Boolean>());
  }

  private void endScope() {
    scopes.pop();
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) {
      return;
    }

    Map<String, Boolean> scope = scopes.peek();

    scope.put(name.lexeme, false);
  }

  private void define(Token name) {
    if (scopes.isEmpty()) {
      return;
    }

    scopes.peek().put(name.lexeme, true);
  }

  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        interpreter.resolve(expr, scopes.size() - 1 - i);

        return;
      }
    }
  }
  private void resolveFunction(Stmt.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;

    currentFunction = type;
    beginScope();

    for (Token param : function.params) {
      declare(param);
      define(param);
    }

    resolve(function.body);
    endScope();
    currentFunction = enclosingFunction;
  }
}
