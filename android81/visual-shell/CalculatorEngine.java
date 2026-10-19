package ro.leo.visual;

import java.math.BigDecimal;
import java.math.MathContext;

/** Decimal arithmetic with normal precedence, unary signs and postfix percent. */
final class CalculatorEngine {
    private static final MathContext PRECISION=MathContext.DECIMAL64;
    private final String input;
    private int position;
    private CalculatorEngine(String input) { this.input=input; }
    static String evaluate(String input) {
        if(input.length()>120) throw new IllegalArgumentException();
        CalculatorEngine parser=new CalculatorEngine(input);
        BigDecimal value=parser.sum();
        if(parser.position!=input.length()) throw new IllegalArgumentException();
        value=value.stripTrailingZeros();
        String plain=value.toPlainString();
        if(plain.length()>120) throw new IllegalArgumentException();
        return plain;
    }
    private boolean take(char value) {
        if(position<input.length() && input.charAt(position)==value) { position++; return true; }
        return false;
    }
    private BigDecimal sum() {
        BigDecimal value=product();
        while(position<input.length()) {
            if(take('+')) value=value.add(product(),PRECISION);
            else if(take('-')) value=value.subtract(product(),PRECISION);
            else break;
        }
        return value;
    }
    private BigDecimal product() {
        BigDecimal value=atom();
        while(position<input.length()) {
            if(take('*')) value=value.multiply(atom(),PRECISION);
            else if(take('/')) value=value.divide(atom(),PRECISION);
            else break;
        }
        return value;
    }
    private BigDecimal atom() {
        if(take('+')) return atom();
        if(take('-')) return atom().negate();
        BigDecimal value;
        if(take('(')) {
            value=sum(); if(!take(')')) throw new IllegalArgumentException();
        } else {
            int start=position;
            while(position<input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position)=='.')) position++;
            if(start==position) throw new IllegalArgumentException();
            value=new BigDecimal(input.substring(start,position),PRECISION);
        }
        while(take('%')) value=value.divide(BigDecimal.valueOf(100),PRECISION);
        return value;
    }
}
