package ro.leo.visual;

/** Values are converted through the category's base unit. */
final class UnitConversion {
    static final String[] CATEGORIES={"Lungime","Masă","Temperatură","Volum","Viteză","Suprafață"};
    static final String[][] UNITS={{"mm","cm","m","km","inch","ft","mile"},{"g","kg","tonă","oz","lb"},{"°C","°F","K"},{"ml","l","m³","galon SUA"},{"km/h","m/s","mph"},{"m²","km²","hectar","ft²"}};
    static final double[][] FACTORS={{.001,.01,1,1000,.0254,.3048,1609.344},{.001,1,1000,.028349523125,.45359237},{1,1,1},{.001,1,1000,3.785411784},{1,3.6,1.609344},{1,1000000,10000,.09290304}};
    static double convert(int category,int from,int to,double value) {
        if(Double.isNaN(value)||Double.isInfinite(value)) throw new IllegalArgumentException();
        double result;
        if(category==2) {
            double c=from==0?value:from==1?(value-32)*5/9:value-273.15;
            if(c < -273.15000001) throw new IllegalArgumentException("Sub zero absolut");
            result=to==0?c:to==1?c*9/5+32:c+273.15;
        } else result=value*FACTORS[category][from]/FACTORS[category][to];
        if(Double.isInfinite(result)||Double.isNaN(result)) throw new IllegalArgumentException();
        return result;
    }
}
