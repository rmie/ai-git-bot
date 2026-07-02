using Xunit;

public class CalculatorTests {
    [Fact]
    public void Add_ShouldReturnSum() {
        var calc = new Calculator();
        Assert.Equal(3, calc.Add(1, 2));
    }
}
