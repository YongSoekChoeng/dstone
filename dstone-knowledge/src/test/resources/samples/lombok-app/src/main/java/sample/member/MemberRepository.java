package sample.member;

/**
 * 저장소 인터페이스. 구현체는 MemoryMemberRepository 하나다.
 */
public interface MemberRepository {

    void save(Member member);

}
