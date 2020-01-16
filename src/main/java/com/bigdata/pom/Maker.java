package com.bigdata.pom;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.dom4j.Element;
import org.dom4j.dom.DOMElement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.alibaba.fastjson.JSONObject;

/**
 * MakePomFromJars
 * <dependencies>
     * <dependency>
         * <groupId>org.dom4j</groupId>
         * <artifactId>dom4j</artifactId>
         * <version>2.0.0</version>
     * </dependency>
         * <dependency>
         * <groupId>com.alibaba</groupId>
         * <artifactId>fastjson</artifactId>
         * <version>1.2.17</version>
     * </dependency>
     * <dependency>
         * <groupId>org.jsoup</groupId>
         * <artifactId>jsoup</artifactId>
         * <version>1.9.2</version>
     * </dependency>
 * </dependencies>
 *
 * @author
 * @date 2016/11/10
 * func: 根据lib包,生成pom依赖文件
 */
public class Maker {

    public static void main ( String[] args ) throws FileNotFoundException, IOException {
        Element dependencys = new DOMElement ( "dependencies" );
        File dir = new File ( "lib" ); // 相对路径
        // 变量check_lib 目录下的全部文件
        for (File jar : dir.listFiles ()) {
            // 1.普通文件对象输入流 转换为 jar对象输入流
            JarInputStream jis = new JarInputStream ( new FileInputStream ( jar ) );
            // 2.提取jar的依赖清单信息
            Manifest mainmanifest = jis.getManifest ( );
            // 关闭jar对象输入流
            jis.close ( );

            // 3.1 如果检测不到依赖信息,则跳过执行下一个jar文件解析
            if (mainmanifest == null) {
                continue;
            }
            ;

            // 3.2如果成功检测到依赖清单,则进行依赖解析
            // 情况 1: Manifest 清单中直接包含扩展名 和 版本信息,直接拼接出完整的jar全路径名称 调用getDependices 检索
            String bundleName = mainmanifest.getMainAttributes ( ).getValue ( "Extension-Name" ); // jar包前半截扩展名称
            String bundleVersion = mainmanifest.getMainAttributes ( ).getValue ( "Manifest-Version" ); // 版本
            Element ele = null;
            StringBuffer sb = new StringBuffer ( jar.getName ( ) ); // jar后半截的包名
            if (bundleName != null) { // 扩展名不为空,直接检索
                bundleName = bundleName.toLowerCase ( ).replace ( " " , "-" ); // 动态凭借完整的jar名
                sb.append ( bundleName + "/t" ).append ( bundleVersion );
                ele = getDependices ( bundleName , bundleVersion ); // 调用下面定义的方法,上网检索指定版本jar的依赖信息
            }

            // 情况2: Manifest 清单中不直接包含扩展名 和 版本信息,而是直接藏在jar的包名中
            if (ele == null || ele.elements ( ).size ( ) == 0) {
                bundleName = "";
                bundleVersion = "";
                // 以 '-' 为分隔符分割,解析出版本 和 扩展名
                String[] ns = jar.getName ( ).replace ( ".jar" , "" ).split ( "-" );
                for (String s : ns) {
                    if (Character.isDigit ( s.charAt ( 0 ) )) {
                        bundleVersion += s + "-"; // 包含数字的片段为版本信息(重新接回"-")
                    } else {
                        bundleName += s + "-"; // 不包含数值的片段为前缀扩展名(重新接回"-")
                    }
                }
                if (bundleVersion.endsWith ( "-" )) { // 如果前面成功解析出版本信息,则删除末尾的"-",如果未解析出版本信息,则不必执行
                    bundleVersion = bundleVersion.substring ( 0 , bundleVersion.length ( ) - 1 );
                }

                // 如果一开始就不能使用"-",分割,后面就不会存在最后的"-"删除前面遍历解析时,带上的最后一个"-",也就不用执行最后"-" 的删除操作
                if (bundleName.endsWith ( "-" )) {
                    bundleName = bundleName.substring ( 0 , bundleName.length ( ) - 1 );
                }
                // 调用自定义的方法,通过 jsoup 联网请求,获取依赖信息
                ele = getDependices ( bundleName , bundleVersion );

                if (ele.elements ( ).size ( ) == 0) { // 未成功检索到,则标记为"not found"
                    ele.add ( new DOMElement ( "groupId" ).addText ( "not find" ) );
                    ele.add ( new DOMElement ( "artifactId" ).addText ( bundleName ) );
                    ele.add ( new DOMElement ( "version" ).addText ( bundleVersion ) );
                }
                // 成功检索到,则需要打印输出
                sb.setLength ( 0 ); // 清空stringBuffer,准重新装载数据
                sb.append ( bundleName + "-" ).append ( bundleVersion );

            }
            dependencys.add ( ele );
            // 输出jar包名
            String search_result = ele.elements ( ).size ( ) == 0 ? "not found" : "success";
            System.out.println ( sb.toString ( ) + ":\t" + search_result );
        }
        System.out.println ( dependencys.asXML ( ) );
    }

    public static Element getDependices ( String key , String ver ) {
        Element dependency = new DOMElement ( "dependency" );
        // 设置代理
        // System.setProperty("http.proxyHost", "127.0.0.1");
        // System.setProperty("http.proxyPort", "8090");
        try {
            // 0.配置url请求链接(从http://search.maven.org/solrsearch检索jar匹配的版本) %22 引号(")
            String url = "http://search.maven.org/solrsearch/select?q=a%3A%22" +
                    key + "%22%20AND%20v%3A%22" + ver + "%22&rows=3&wt=json";
            // 1.使用网页爬虫jsoup 从指定url链接获取json格式信息
            Document doc = Jsoup.connect ( url ).ignoreContentType ( true ).timeout ( 30000 ).get ( );
            // 2.提取响应报文
            String elem = doc.body ( ).text ( );
            JSONObject response = JSONObject.parseObject ( elem ).getJSONObject ( "response" );
            // 3.解析响应信息
            if (response.containsKey ( "docs" ) && response.getJSONArray ( "docs" ).size ( ) > 0) {
                // 截取docs 片段,转化为json对象方便进一步解析
                JSONObject docJson = response.getJSONArray ( "docs" ).getJSONObject ( 0 );
                Element groupId = new DOMElement ( "groupId" );
                Element artifactId = new DOMElement ( "artifactId" );
                Element version = new DOMElement ( "version" );
                // 拼接<dependency>
                groupId.addText ( docJson.getString ( "g" ) );
                artifactId.addText ( docJson.getString ( "a" ) );
                version.addText ( docJson.getString ( "v" ) );
                dependency.add ( groupId );
                dependency.add ( artifactId );
                dependency.add ( version );
            }
        } catch (Exception e) {
            e.printStackTrace ( );
        }
        return dependency;
    }

}
