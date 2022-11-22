package com.linmt.processor;

import com.google.auto.service.AutoService;
import com.linmt.annotation.Data;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: Linmt@datagoo.cn
 * Date: 2022/11/21
 * Time: 20:30
 * Description: @Data处理器
 * https://blog.csdn.net/youanyyou/article/details/120582478
 */
@AutoService(Processor.class)
public class DataProcessor extends AbstractProcessor {
    private Messager messager;
    private JavacTrees javacTrees;
    private TreeMaker treeMaker;
    private Names names;

    // 定义需要处理的注解
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(Data.class.getCanonicalName());
        return annotations;
    }

    // 版本支持
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    // 初始化
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.javacTrees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);

        log("初始化....");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Data.class);
        for (Element element : elements) {
            log("当前类=" + element.getSimpleName());
            JCTree jcTree = javacTrees.getTree(element);

            // 以下这段代码解决报错java.lang.AssertionError: Value of x -1
            treeMaker.pos = jcTree.pos;
            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    List<JCTree.JCVariableDecl> jcVariableDeclList = List.nil();

                    // jcClassDecl.defs为类里面的所有定义
                    for (JCTree tree : jcClassDecl.defs) {
                        // 判断是变量
                        if (tree.getKind().equals(Tree.Kind.VARIABLE)) {
                            JCTree.JCVariableDecl jcVariableDecl = (JCTree.JCVariableDecl) tree;
                            // 此处能用com.sun.tools.javac.util.List的add方法
                            jcVariableDeclList = jcVariableDeclList.append(jcVariableDecl);
                            log("已读取字段=" + jcVariableDecl.getName().toString());
                        }
                    }

                    for (JCTree.JCVariableDecl jcVariableDecl : jcVariableDeclList) {
                        // 追加类里面的定义
                        jcClassDecl.defs = jcClassDecl.defs.append(buildGetterMethod(jcVariableDecl));
                        jcClassDecl.defs = jcClassDecl.defs.append(buildSetterMethod(jcVariableDecl));
                    }
                    super.visitClassDef(jcClassDecl);
                }
            });
        }

        return true;
    }

    /***
     * 构建get方法的JCTree.JCMethodDecl对象
     * @param jcVariableDecl
     * @return
     */
    private JCTree.JCMethodDecl buildGetterMethod(JCTree.JCVariableDecl jcVariableDecl) {
        // 构建方法体
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        statements.append(treeMaker.Return(treeMaker.Select(treeMaker.Ident(names.fromString("this")), jcVariableDecl.getName())));
        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());

        // JCTree.JCMethodDecl不能直接使用new关键字来创建,可以通过treeMaker.MethodDef来创建
        JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(
                treeMaker.Modifiers(Flags.PUBLIC), // 访问标志
                generateGetMethodName(jcVariableDecl.getName()), // 方法名
                jcVariableDecl.vartype, // 返回参数
                List.nil(), // 泛型参数列表
                List.nil(), // 参数列表
                List.nil(), // 异常声明列表
                body, // 方法体
                null
        );

        return jcMethodDecl;
    }

    /***
     * 构建set方法的JCTree.JCMethodDecl对象
     * @param jcVariableDecl
     * @return
     */
    private JCTree.JCMethodDecl buildSetterMethod(JCTree.JCVariableDecl jcVariableDecl) {
        // 构建方法体
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        statements.append(treeMaker.Exec(
                // this.var = var;
                // treeMaker.Assign 赋值语句
                treeMaker.Assign(
                        treeMaker.Select(treeMaker.Ident(names.fromString("this")), jcVariableDecl.getName()),
                        treeMaker.Ident(jcVariableDecl.getName())
                )
        ));
        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());

        List<JCTree.JCVariableDecl> params = List.of(
                treeMaker.VarDef(
                        treeMaker.Modifiers(Flags.PARAMETER, List.nil()),
                        jcVariableDecl.name, // 参数名
                        jcVariableDecl.vartype, // 类型
                        null // 初始值
                )
        );

        JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(
                treeMaker.Modifiers(Flags.PUBLIC), // 访问标志
                generateSetMethodName(jcVariableDecl.getName()), // 方法名 = setVar
                treeMaker.Type(new Type.JCVoidType()), // 返回参数=void
                List.nil(), // 泛型参数列表
                params, // 参数列表
                List.nil(), // 异常声明列表
                body, // 方法体
                null
        );

        return jcMethodDecl;
    }

    /**
     * 生成get方法的名称
     *
     * @param name
     * @return
     */
    private Name generateGetMethodName(Name name) {
        String s = name.toString();
        return names.fromString("get" + s.substring(0, 1).toUpperCase() + s.substring(1, name.length()));
    }

    /**
     * 生成set方法的名称
     *
     * @param name
     * @return
     */
    private Name generateSetMethodName(Name name) {
        String s = name.toString();
        return names.fromString("set" + s.substring(0, 1).toUpperCase() + s.substring(1, name.length()));
    }


    /**
     * 打印日志
     *
     * @param msg
     */
    private void log(String msg) {
        messager.printMessage(Diagnostic.Kind.NOTE, msg);
    }
}
