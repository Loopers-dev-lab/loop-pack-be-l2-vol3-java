package com.loopers.domain.member;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class MemberRepositoryTest {
    
    @Autowired
    private MemberRepository memberRepository;
    
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    
    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }
    
    @DisplayName("회원을 저장할 때")
    @Nested
    class Save {
        
        @DisplayName("유효한 회원 정보를 저장하면 성공한다")
        @Test
        void saveMember() {
            Member member = new Member(
                "ymcho",
                "ymcho123",
                "조용민",
                "1991-07-03",
                "ymcho@example.com"
            );
            
            Member saved = memberRepository.save(member);
            
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getLoginId()).isEqualTo("ymcho"),
                () -> assertThat(saved.getName()).isEqualTo("조용민")
            );
        }
    }
    
    @DisplayName("로그인ID로 회원을 조회할 때")
    @Nested
    class FindByLoginId {
        
        @DisplayName("존재하는 로그인ID로 조회하면 회원을 반환한다")
        @Test
        void findExistingMember() {
            Member member = new Member(
                "ymcho",
                "ymcho123",
                "조용민",
                "1991-07-03",
                "ymcho@example.com"
            );
            memberRepository.save(member);
            
            Optional<Member> found = memberRepository.findByLoginId("ymcho");
            
            assertAll(
                () -> assertThat(found).isPresent(),
                () -> assertThat(found.get().getLoginId()).isEqualTo("ymcho"),
                () -> assertThat(found.get().getName()).isEqualTo("조용민")
            );
        }
        
        @DisplayName("존재하지 않는 로그인ID로 조회하면 빈 값을 반환한다")
        @Test
        void findNonExistingMember() {
            Optional<Member> found = memberRepository.findByLoginId("nonexistent");
            
            assertThat(found).isEmpty();
        }
    }
    
    @DisplayName("로그인ID 중복을 확인할 때")
    @Nested
    class ExistsByLoginId {
        
        @DisplayName("이미 존재하는 로그인ID면 true를 반환한다")
        @Test
        void existingLoginId() {
            Member member = new Member(
                "ymcho",
                "ymcho123",
                "조용민",
                "1991-07-03",
                "ymcho@example.com"
            );
            memberRepository.save(member);
            
            boolean exists = memberRepository.existsByLoginId("ymcho");
            
            assertThat(exists).isTrue();
        }
        
        @DisplayName("존재하지 않는 로그인ID면 false를 반환한다")
        @Test
        void nonExistingLoginId() {
            boolean exists = memberRepository.existsByLoginId("nonexistent");
            
            assertThat(exists).isFalse();
        }
    }
}
