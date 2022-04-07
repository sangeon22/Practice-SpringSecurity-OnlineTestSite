package com.sp.fc.user.repository;

import com.sp.fc.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // username Update
    @Modifying(clearAutomatically = true)
    @Query("update User set name=?2, updated=?3 where userId=?1")
    void updateUserName(Long userId, String userName, LocalDateTime update);

    // email로 username 검색 - UserSecurityService에서 사용하려고 만듬
    Optional<User> findByEmail(String username);

    // Authority가 user student와 teacher를 구분하는 용도로도 쓰이고 있기 때문에 권한과 ROLE의 역할을 함
    // 아래 메소드를 통해 사용자 List, teacher List를 받을 수 있다.
    @Query("select a from User a, Authority b where a.userId=b.userId and b.authority=?1")
    List<User> findAllByAuthoritiesIn(String authority);

    // UI에서 구현하려고 Paging된 사용자 List, teacher List
    @Query("select a from User a, Authority b where a.userId=b.userId and b.authority=?1")
    Page<User> findAllByAuthoritiesIn(String authority, Pageable pageable);

    // 학교 조회
    @Query("select a from User a, Authority b where a.school.schoolId=?1 and a.userId=b.userId and b.authority=?2")
    List<User> findAllBySchool(Long schoolId, String authority);

    // teacher의 학생 List 조회
    @Query("select a from User a, User b where a.teacher.userId=b.userId and b.userId=?1")
    List<User> findAllByTeacherUserId(Long userId);

    // 아래는 count 메소드
    @Query("select count(a) from User a, User b where a.teacher.userId=b.userId and b.userId=?1")
    Long countByAllTeacherUserId(Long userId);

    @Query("select count(a) from User a, Authority b where a.userId=b.userId and b.authority=?1")
    long countAllByAuthoritiesIn(String authority);

    @Query("select count(a) from User a, Authority b where a.school.schoolId=?1 and a.userId=b.userId and b.authority=?2")
    long countAllByAuthoritiesIn(long schoolId, String authority);



}
