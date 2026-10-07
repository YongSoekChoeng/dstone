package cyc;
public class Recursion {
    // 자기 자신을 부른다
    public int self(int n) { return n <= 0 ? 0 : self(n - 1); }
    // 서로를 부른다: a → b → c → a
    public void a() { b(); }
    public void b() { c(); }
    public void c() { a(); }
    public void entry() { a(); self(3); }
}
