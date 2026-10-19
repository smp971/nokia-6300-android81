package ro.leo.visual;

/** Host checks for arithmetic behavior, independent of Android UI. */
public final class CalculatorEngineTest {
    public static void main(String[] args) {
        String[][] valid={
            {"2+2","4"},{"2+3*4","14"},{"(2+3)*4","20"},{"10-3-2","5"},
            {"12/3*2","8"},{"0.1+0.2","0.3"},{"1/8","0.125"},{"-3*2","-6"},
            {"2*-3","-6"},{"-(2+3)","-5"},{"50%","0.5"},{"200*10%","20"},
            {"10+50%","10.5"},{".5+.5","1"},{"2--3","5"},{"100/4%","2500"},
            {"0/7","0"},{"1/3","0.3333333333333333"},{"0.00001*0.01","0.0000001"}
        };
        for(String[] test:valid) {
            String value=CalculatorEngine.evaluate(test[0]);
            if(!test[1].equals(value)) throw new AssertionError(test[0]+" => "+value);
        }
        String[] invalid={"","1+","(1+2","1..2","2(3)","1e3","hello",".","1/0","1/(2-2)"};
        for(String test:invalid) {
            try { CalculatorEngine.evaluate(test); throw new AssertionError("Accepted: "+test); }
            catch(IllegalArgumentException expected) { }
            catch(ArithmeticException expected) { }
        }
        System.out.println("PASS "+valid.length+" results and "+invalid.length+" invalid/zero-division cases");
    }
}
