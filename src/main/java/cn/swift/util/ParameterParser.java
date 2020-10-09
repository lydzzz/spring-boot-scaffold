/**
 * Copyright (C) 2020 ~ 2020 Meituan. All Rights Reserved.
 */
package cn.swift.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSON;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.javadoc.Javadoc;
import com.google.common.collect.Lists;

/**
 * @author:   		HuLin
 * @date:    		Aug 11, 2020 11:38:52 AM
 * @description:    DESCRIPTION
 * @version:   		v1.0
 */
public class ParameterParser {
    
    public void batchParse(File file, List<ParameterClass> pcs) throws FileNotFoundException {
        if(file.isDirectory()) {
            for(File subFile : file.listFiles()) {
                batchParse(subFile, pcs);
            }
        } else {
            String className = file.getName().substring(0, file.getName().indexOf("."));
            List<Parameter> parameters = parse(file);
            if(CollectionUtils.isEmpty(parameters)) {
                return;
            }
            ParameterClass pc = new ParameterClass();
            pc.setClassName(className);
            pc.setParmeters(parameters);
            pcs.add(pc);
        }
    }
    
    public List<Parameter> parse(String codePath) throws FileNotFoundException {
        File code = new File(codePath);
        return parse(code);
    }

    public List<Parameter> parse(File code) throws FileNotFoundException {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(LanguageLevel.JAVA_6);
        config.setCharacterEncoding(Charset.forName("GBK"));
        JavaParser parser = new JavaParser(config);
        ParseResult<CompilationUnit> parseResult = parser.parse(code);
        final ParameterVisitor visitor = new ParameterVisitor();
        parseResult.getResult().ifPresent(unit -> {
            if(unit.getType(0) instanceof EnumDeclaration) {
                return;
            }
            unit.accept(visitor, null);
            });
        return visitor.getParameters();
    }
    
    public static void main(String[] args) throws Exception {
        File folder = new File("/Users/swift/workspace/git/code/cc-comb/comb-infrastructure/"
                + "src/main/java/com/meituan/cbc/param/def/");
        List<ParameterClass> pcs = Lists.newArrayList();
        new ParameterParser().batchParse(folder, pcs);
        System.out.println(JSON.toJSONString(pcs));
//        for(ParameterClass pc : pcs) {
//            ExcelUtil.exportExcel("/Users/swift/Desktop/parameter/" + pc.getClassName() + ".xls", 
//                    "名称:name,类型:type,长度:length,精度:precision,描述:remark", 
//                    pc.getParmeters(), "cn.swift.util.Parameter");
//        }
    }
    
}

class ParameterVisitor extends VoidVisitorAdapter<Void> {
    
    private List<Parameter> parmeters;
    
    ParameterVisitor(){
        this.parmeters = Lists.newArrayList();
    }
    
    public List<Parameter> getParameters(){
        return this.parmeters;
    }
    
    @Override
    public void visit(FieldDeclaration field, Void arg) {
        if("serialVersionUID".equals(field.getVariables().get(0).getName().asString())) {
            super.visit(field, arg);
            return;
        }
        Parameter parameter = new Parameter();
        parameter.setName(field.getVariables().get(0).getName().asString());
        parameter.setType(field.getVariables().get(0).getType().asString());
        
        if(field.getAnnotations().isNonEmpty()) {
            Optional<AnnotationExpr> annotationExpr = field.getAnnotations().stream()
                    .filter(ann -> "PropertyInfo".equals(ann.getNameAsString()))
                    .findAny();
            annotationExpr.ifPresent(ann -> populateFieldInfo(ann, parameter));
        }
        
        if(StringUtils.isBlank(parameter.getRemark())) {
            populateFieldInfo(field.getJavadoc(), parameter);
        }
        
        parmeters.add(parameter);
    }

    private void populateFieldInfo(Optional<Javadoc> javadoc, Parameter parameter) {
        javadoc.ifPresent(doc -> 
            parameter.setRemark(doc.getDescription().getElements().get(0).toText()));
    }

    private void populateFieldInfo(AnnotationExpr annotationExpr, Parameter parameter) {
        if(annotationExpr instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr annotation = (SingleMemberAnnotationExpr) annotationExpr;
            populateFieldInfo(annotation, parameter);
        }
        if(annotationExpr instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr annotation = (NormalAnnotationExpr) annotationExpr;
            populateFieldInfo(annotation, parameter);
        }
    }

    private void populateFieldInfo(NormalAnnotationExpr annotation, Parameter parameter) {
        NodeList<MemberValuePair> pairs = annotation.getPairs();
        for(MemberValuePair pair : pairs) {
            if("name".equals(pair.getName().asString())) {
                String nameValue = pair.getValue().toString();
                nameValue = nameValue.substring(1, nameValue.length() - 1);
                parameter.setRemark(nameValue);
            }
            if("length".equals(pair.getName().asString())) {
                String length = pair.getValue().toString();
                parameter.setLength(length);
            }
            if("precision".equals(pair.getName().asString())) {
                String precision = pair.getValue().toString();
                parameter.setPrecision(precision);
            }
        }
    }

    private void populateFieldInfo(SingleMemberAnnotationExpr annotation,
            Parameter parameter) {
        parameter.setRemark(annotation.getMemberValue().toString());
    }
    
}

