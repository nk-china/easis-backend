import cn.nkpro.elcube.basic.secret.DesCbcUtil;
import cn.nkpro.elcube.utils.UUIDHexGenerator;
import org.junit.Test;

public class TestSimple {


    @Test
    public void test() throws Exception {

        System.out.println(String.format("%012d",1));
        String plain =
                "{id:'"+ UUIDHexGenerator.generate().replaceAll("-","").toUpperCase()+"',name:'纽扣互联（北京）科技有限公司',expire:'20991231'}";

        System.out.println(plain);
        String str = DesCbcUtil.encode(plain,"b2c17b46e2b1415392aab5a82869856c",
                "61960842");
        System.out.println(
            str
        );

        System.out.println(Integer.MAX_VALUE);

//        System.out.println(DesCbcUtil.decode("FM0cn97Fn2elThYej9M2409D08zwCGEPpDi3OI2ihy8gFN/ACnknIl3uR/6FxSjxZy+KO+BmodWpFFszxHlRxQcFsfAzXQZk0Hocdm8FYOG1Bt5bWyc6C2b1t6aMrm9wsZs2aSe7xDx8p0Mgyj8apM7klxQhl6Ge","b2c17b46e2b1415392aab5a82869856c","61960842"));;
    }
}
