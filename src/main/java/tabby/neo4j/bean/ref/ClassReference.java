package tabby.neo4j.bean.ref;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import soot.SootClass;
import tabby.config.GlobalConfiguration;
import tabby.core.data.RulesContainer;
import tabby.core.data.TabbyRule;
import tabby.neo4j.bean.edge.Extend;
import tabby.neo4j.bean.edge.Has;
import tabby.neo4j.bean.edge.Interfaces;
import tabby.neo4j.bean.ref.handle.ClassRefHandle;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Getter
@Setter
@NodeEntity(label="Class")
public class ClassReference{

    @Id
    @Convert(UuidStringConverter.class)
    private UUID uuid;

    private String type = "Class";

    private String name;

    private String superClass;

    private List<String> interfaces = new ArrayList<>();

    private boolean isInterface = false;
    private boolean hasSuperClass = false;
    private boolean hasInterfaces = false;
    private transient boolean isInitialed = false;
    /**
     * [[name, modifiers, type],...]
     */
    private Set<String> fields = new HashSet<>();

//    private Set<String> annotations = new HashSet<>();

    // neo4j relationships
    /**
     * 继承边
     */
    @Relationship(type="EXTEND")
    private Extend extendEdge = null;

    /**
     * 类成员函数 has边
     * Object A has Method B
     */
    @Relationship(type="HAS", direction = "UNDIRECTED")
    private List<Has> hasEdge = new ArrayList<>();

    /**
     * 接口继承边
     * 双向，可以从实现类A到接口B，也可以从接口类B到实现类A
     * A -[:INTERFACE]-> B
     * B -[:INTERFACE]-> A
     */
    @Relationship(type="INTERFACE", direction = "UNDIRECTED")
    private Set<Interfaces> interfaceEdge = new HashSet<>();

    public ClassRefHandle getHandle(){
        return new ClassRefHandle(this.name);
    }

    public static ClassReference parse(SootClass cls, RulesContainer rulesContainer){
        ClassReference classRef = newInstance(cls.getName());
        classRef.setInterface(cls.isInterface());
        Set<String> relatedClassnames = getAllFatherNodes(cls);
        // 提取父类信息
        if(cls.hasSuperclass() && !cls.getSuperclass().getName().equals("java.lang.Object")){
            // 剔除Object类的继承关系，节省继承边数量
            classRef.setHasSuperClass(cls.hasSuperclass());
            classRef.setSuperClass(cls.getSuperclass().getName());
        }
        // 提取接口信息
        if(cls.getInterfaceCount() > 0){
            classRef.setHasInterfaces(true);
            cls.getInterfaces().forEach((intface) -> {
                classRef.getInterfaces().add(intface.getName());
            });
        }
        // 提取类属性信息
        if(cls.getFieldCount() > 0){
            cls.getFields().forEach((field) -> {
                List<String> fieldInfo = new ArrayList<>();
                fieldInfo.add(field.getName());
                fieldInfo.add(field.getModifiers()+"");
                fieldInfo.add(field.getType().toString());
                classRef.getFields().add(GlobalConfiguration.GSON.toJson(fieldInfo));
            });
        }
        // 提取类函数信息
        if(cls.getMethodCount() > 0){
            cls.getMethods().forEach((method) -> {
                MethodReference methodRef = MethodReference.parse(classRef.getHandle(), method);

                TabbyRule.Rule rule = rulesContainer.getRule(classRef.getName(), methodRef.getName());
                if(rule == null){ // 对于ignore类型，支持多级父类和接口的规则查找
                    for(String classname:relatedClassnames){
                        rule = rulesContainer.getRule(classname, methodRef.getName());
                        if(rule != null && rule.isIgnore()){
                            break;
                        }
                    }
                }
                boolean isSink = rule != null && rule.isSink();
                boolean isIgnore = rule != null && rule.isIgnore();
                boolean isSource = rule != null && rule.isSource();

                methodRef.setSink(isSink);
                methodRef.setPolluted(isSink);
                methodRef.setIgnore(isIgnore);
                methodRef.setSource(isSource);

                if(rule != null){
                    methodRef.setActions(rule.getActions());
                    methodRef.setPollutedPosition(rule.getPolluted());
                    methodRef.setInitialed(true);
                    methodRef.setActionInitialed(true);
                }

                Has has = Has.newInstance(classRef, methodRef);
                classRef.getHasEdge().add(has);
            });
        }

        return classRef;
    }

    public static Set<String> getAllFatherNodes(SootClass cls){
        Set<String> nodes = new HashSet<>();
        if(cls.hasSuperclass() && !cls.getSuperclass().getName().equals("java.lang.Object")){
            nodes.add(cls.getSuperclass().getName());
            nodes.addAll(getAllFatherNodes(cls.getSuperclass()));
        }
        if(cls.getInterfaceCount() > 0){
            cls.getInterfaces().forEach(intface -> {
                nodes.add(intface.getName());
                nodes.addAll(getAllFatherNodes(intface));
            });
        }
        return nodes;
    }

    public static ClassReference newInstance(String name){
        ClassReference classRef = new ClassReference();
        classRef.setUuid(UUID.randomUUID());
        classRef.setName(name);
        classRef.setInterfaces(new ArrayList<>());
        classRef.setFields(new HashSet<>());
//        classRef.setAnnotations(new HashSet<>());
        return classRef;
    }

    public List<String> toCSV(){
        List<String> ret = new ArrayList<>();
        ret.add(uuid.toString());
        ret.add(name);
        ret.add(superClass);
        ret.add(String.join("|", interfaces));
        ret.add(Boolean.toString(isInterface));
        ret.add(Boolean.toString(hasSuperClass));
        ret.add(Boolean.toString(hasInterfaces));
        ret.add(String.join("|", fields));
        return ret;
    }

}
