package cyc;
// 컴파일은 안 되지만 소스로는 존재할 수 있는 순환 상속
public class CycleA extends CycleB { public void run() { helper(); missing(); } public void helper() {} }
