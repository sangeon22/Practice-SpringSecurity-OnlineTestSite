package com.sp.fc.paper.service;

import com.sp.fc.paper.domain.PaperTemplate;
import com.sp.fc.paper.domain.Problem;
import com.sp.fc.paper.repository.PaperTemplateRepository;
import com.sp.fc.user.domain.Authority;
import com.sp.fc.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Transactional
@Service
@RequiredArgsConstructor
public class PaperTemplateService {

    private final PaperTemplateRepository paperTemplateRepository;
    private final ProblemService problemService;

    // PaperTemplate save
    public PaperTemplate save(PaperTemplate paperTemplate) {
        if(paperTemplate.getPaperTemplateId() == null){
            paperTemplate.setCreated(LocalDateTime.now());
        }
        paperTemplate.setUpdated(LocalDateTime.now());
        return paperTemplateRepository.save(paperTemplate);
    }

    //시험지에 문제를 추가
    public Problem addProblem(long paperTemplateId, Problem problem){
        problem.setPaperTemplateId(paperTemplateId);
        //paperTemplateId로 템플릿을 가져온 후,
        return findById(paperTemplateId).map(paperTemplate -> {
            if(paperTemplate.getProblemList() == null){
                paperTemplate.setProblemList(new ArrayList<>());
            }
            //problem list에 문제를 추가
            problem.setCreated(LocalDateTime.now());
            paperTemplate.getProblemList().add(problem);
            //앞에서부터 가져온 순서대로 indexNum을 부여한다. 문제번호 부여
            IntStream.rangeClosed(1, paperTemplate.getProblemList().size()).forEach(i->{
                paperTemplate.getProblemList().get(i-1).setIndexNum(i);
            });
            // total size를 업데이트할 때 마다 추가하도록 한 후 저장
            paperTemplate.setTotal(paperTemplate.getProblemList().size());
            Problem saved = problemService.save(problem);
            save(paperTemplate);
            return saved;
        }).orElseThrow(()-> new IllegalArgumentException(paperTemplateId+" 아이디 시험지가 없습니다."));
    }


    //PaperTemplate을 찾아오는 것것
   public Optional<PaperTemplate> findById(long paperTemplateId) {
        return paperTemplateRepository.findById(paperTemplateId);
    }

    //
    public PaperTemplate removeProblem(long paperTemplateId, long problemId){
        return findById(paperTemplateId).map(paperTemplate -> {
            if(paperTemplate.getProblemList() == null){
                return paperTemplate;
            }
            // problem list에서 해당 번호를 가진 문제가 존재한다면 삭제하도록록
            Optional<Problem> problem = paperTemplate.getProblemList().stream().filter(p -> p.getProblemId().equals(problemId)).findFirst();
            if(problem.isPresent()){
                paperTemplate.setProblemList(
                        paperTemplate.getProblemList().stream().filter(p -> !p.getProblemId().equals(problemId))
                                .collect(Collectors.toList())
                );
                problemService.delete(problem.get());
                IntStream.rangeClosed(1, paperTemplate.getProblemList().size()).forEach(i->{
                    paperTemplate.getProblemList().get(i-1).setIndexNum(i);
                });
            }
            paperTemplate.setTotal(paperTemplate.getProblemList().size());
            return save(paperTemplate);
        }).orElseThrow(()-> new IllegalArgumentException(paperTemplateId+" 아이디 시험지가 없습니다."));
    }


    // problem의 내용을 수정하는 것
    public void update(long problemId, String content, String answer){
        problemService.updateProblem(problemId, content, answer);
    }

    // ProblemTemplate을 id로 찾아오는 것
    @Transactional(readOnly = true)
    public Optional<PaperTemplate> findProblemTemplate(Long paperTemplateId) {
        return paperTemplateRepository.findById(paperTemplateId).map(pt->{
            if(pt.getProblemList().size() != pt.getTotal()){ // lazy 해결위해 체크...
                pt.setTotal(pt.getProblemList().size());
            }
            if(pt.getUserId() != ((User)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId())
            {
                if(!((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getAuthorities().contains(Authority.ADMIN_AUTHORITY)){
                    throw new AccessDeniedException("access denied");
                }
            }
            return pt;
        });
    }

    // 정답을 체크하기 위한 Map
    @Transactional(readOnly = true)
    public Map<Integer, String> getPaperAnswerSheet(Long paperTemplateId) {
        Optional<PaperTemplate> template = findById(paperTemplateId);
        if(!template.isPresent()) return new HashMap<>();
        return template.get().getProblemList().stream().collect(Collectors.toMap(Problem::getIndexNum, Problem::getAnswer));
    }

    // teacherId로 template을 가져옴
    @Transactional(readOnly = true)
    public List<PaperTemplate> findByTeacherId(Long userId) {
        return paperTemplateRepository.findAllByUserIdOrderByCreatedDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<PaperTemplate> findByTeacherId(Long userId, int pageNum, int size) {
        return paperTemplateRepository.findAllByUserIdOrderByCreatedDesc(userId, PageRequest.of(pageNum-1, size));
    }

    @Transactional(readOnly = true)
    public Object countByUserId(Long userId) {
        return paperTemplateRepository.countByuserId(userId);
    }

    public void updatePublishedCount(long paperTemplateId, long publishedCount) {
        paperTemplateRepository.findById(paperTemplateId).ifPresent(paperTemplate -> {
            paperTemplate.setPublishedCount(publishedCount);
            paperTemplateRepository.save(paperTemplate);
        });
    }

    public void updateCompleteCount(Long paperTemplateId) {
        paperTemplateRepository.findById(paperTemplateId).ifPresent(paperTemplate -> {
            paperTemplate.setCompleteCount(paperTemplate.getCompleteCount()+1);
            paperTemplateRepository.save(paperTemplate);
        });
    }
}
