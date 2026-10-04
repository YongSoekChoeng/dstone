package sample.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Member 의 Lombok 멤버를 부르는 쪽. 여기 있는 호출은 전부 소스에 없는 멤버를 가리킨다.
 */
@Slf4j
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public Member register(String id, String name) {
        Member member = Member.builder().id(id).name(name).build();
        member.setActive(true);
        log.info("register " + member.getName());
        memberRepository.save(member);
        return member;
    }

    public String describe(Member member) {
        return member.getId() + ":" + member.getName() + ":" + member.isActive();
    }

}
