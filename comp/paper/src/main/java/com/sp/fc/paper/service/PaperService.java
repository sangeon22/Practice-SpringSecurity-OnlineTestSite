package com.sp.fc.paper.service;


import com.sp.fc.paper.domain.Paper;
import com.sp.fc.paper.domain.PaperAnswer;
import com.sp.fc.paper.repository.PaperAnswerRepository;
import com.sp.fc.paper.repository.PaperRepository;
import com.sp.fc.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional
public class PaperService {

    private final UserRepository userRepository;
    private final PaperTemplateService paperTemplateService;
    private final PaperRepository paperRepository;
    private final PaperAnswerRepository paperAnswerRepository;

    protected Paper save(Paper paper){
        if(paper.getPaperId() == null){
            paper.setCreated(LocalDateTime.now());
        }
        return paperRepository.save(paper);
    }

    // 학생들에게 studentId list를 주고 시험지를 나눠줄 때 이 메소드를 호출
    @Transactional
    public List<Paper> publishPaper(long paperTemplateId, List<Long> studyIdList){
        List<Paper> papers = paperTemplateService.findById(paperTemplateId).map(paperTemplate ->
                StreamSupport.stream(userRepository.findAllById(studyIdList).spliterator(), false)
                        .map(study -> {
                            Paper paper = Paper.builder()
                                    .paperTemplateId(paperTemplate.getPaperTemplateId())
                                    .name(paperTemplate.getName())
                                    .state(Paper.PaperState.READY)
                                    .studyUserId(study.getUserId())
                                    .total(paperTemplate.getTotal())
                                    .build();
                            return save(paper);
                        }).collect(Collectors.toList())
        ).orElseThrow(()->new IllegalArgumentException(paperTemplateId+" 시험지 템플릿이 존재하지 않습니다."));
        paperTemplateService.updatePublishedCount(paperTemplateId, papers.size());
        return papers;
    }

    // paper를 remove
    // paper에 연결된 answer들은 동시에 삭제
    public void removePaper(long paperTemplateId, List<Long> studyIdList){
        paperRepository.findAllByPaperTemplateIdAndStudyUserIdIn(paperTemplateId, studyIdList)
                .forEach(paper -> {
                    paperRepository.delete(paper);
                });
    }

    // paperId로 paper를 찾은 뒤,
    @Transactional
    public void answer(Long paperId, Long problemId, int num, String answer){
        paperRepository.findById(paperId).ifPresentOrElse(paper->{
            //해당 paper answer가 있는지 조사
            Optional<PaperAnswer> pa = paper.getPaperAnswerList() == null ? Optional.empty() :
                    paper.getPaperAnswerList().stream().filter(a -> a.getId().getNum() == num).findFirst();
            if(pa.isPresent()){
                //있으면 마킹한 내용을 업데이트
                PaperAnswer pAnswer = pa.get();
                pAnswer.setAnswer(answer);
                pAnswer.setAnswered(LocalDateTime.now());
                pAnswer.setProblemId(problemId);
                paperAnswerRepository.save(pAnswer);
            }else{
                //없으면 만들어서 넣어주면서 셋팅후 state 변경
                PaperAnswer pAnswer = PaperAnswer.builder()
                        .id(new PaperAnswer.PaperAnswerId(paperId, num))
                        .problemId(problemId)
                        .answer(answer)
                        .answered(LocalDateTime.now())
                        .build();
//                paperAnswerRepository.save(pAnswer);
                pAnswer.setPaper(paper);
                if(paper.getPaperAnswerList() == null) paper.setPaperAnswerList(new ArrayList<>());
                paper.getPaperAnswerList().add(pAnswer);
                paper.addAnswered();
                if(paper.getState() != Paper.PaperState.START){
                    paper.setState(Paper.PaperState.START);
                    paper.setStartTime(LocalDateTime.now());
                }
                paperRepository.save(paper);
            }
        }, ()->new IllegalArgumentException(paperId+" 시험지를 찾을 수 없습니다."));
    }

    // 시험지 제출
    @Transactional
    public void paperDone(Long paperId){
        // 시험을 끝냈으면 해당 시험지의 답안을 비교해서 채점 한다.
        final Paper paper = paperRepository.findById(paperId).orElseThrow(()->new IllegalArgumentException(paperId+" 시험지를 찾을 수 없습니다."));
        final Map<Integer, String> answerSheet = paperTemplateService.getPaperAnswerSheet(paper.getPaperTemplateId());
        paper.setCorrect(0);
        if(paper.getPaperAnswerList() != null) {
            paper.getPaperAnswerList().forEach(answer -> {
                if (answer.getAnswer() != null && answer.getAnswer().equals(answerSheet.get(answer.getId().getNum()))) {
                    answer.setCorrect(true);
                    paper.addCorrect();
                    paperAnswerRepository.save(answer);
                }
            });
        }
        paper.setState(Paper.PaperState.END);
        paper.setEndTime(LocalDateTime.now());
        Paper saved = paperRepository.save(paper);
//        paperTemplateService.findById(saved.getPaperTemplateId()).map(paperTemplate -> paperTemplate.setCompleteCount(saved.))
        paperTemplateService.updateCompleteCount(saved.getPaperTemplateId());
    }

    //paper list get
    @Transactional(readOnly = true)
    public List<Paper> getPapers(Long paperTemplateId) {
        return paperRepository.findAllByPaperTemplateId(paperTemplateId);
    }

    // 나에게 배부된 시험지 list get
    @Transactional(readOnly = true)
    public List<Paper> getPapersByUser(Long studyUserId) {
        return paperRepository.findAllByStudyUserIdOrderByCreatedDesc(studyUserId);
    }

    // 완료된, 진행중인 시험지 list
    @Transactional(readOnly = true)
    public List<Paper> getPapersByUserState(Long studyUserId, Paper.PaperState state) {
        return paperRepository.findAllByStudyUserIdAndStateOrderByCreatedDesc(studyUserId, state);
    }

    @Transactional(readOnly = true)
    public List<Paper> getPapersByUserIng(Long studyUserId) {
        return paperRepository.findAllByStudyUserIdAndStateInOrderByCreatedDesc(studyUserId, List.of(Paper.PaperState.READY, Paper.PaperState.START));
    }

    @Transactional(readOnly = true)
    public long countPapersByUserIng(Long studyUserId) {
        return paperRepository.countByStudyUserIdAndStateIn(studyUserId, List.of(Paper.PaperState.READY, Paper.PaperState.START));
    }

    @Transactional(readOnly = true)
    public Page<Paper> getPapersByUserResult(Long studyUserId, int pageNum, int size) {
        return paperRepository.findAllByStudyUserIdAndStateOrderByCreatedDesc(studyUserId, Paper.PaperState.END, PageRequest.of(pageNum-1, size));
    }

    @Transactional(readOnly = true)
    public long countPapersByUserResult(Long studyUserId) {
        return paperRepository.countByStudyUserIdAndState(studyUserId, Paper.PaperState.END);
    }

    @Transactional(readOnly = true)
    public List<Paper> getPapersByUserState(Long studyUserId, List<Paper.PaperState> states) {
        return paperRepository.findAllByStudyUserIdAndStateInOrderByCreatedDesc(studyUserId, states);
    }

    @PostAuthorize("returnObject.isEmpty() || returnObject.get().studyUserId == principal.userId")
    @Transactional(readOnly = true)
    public Optional<Paper> findPaper(Long paperId) {
        return paperRepository.findById(paperId).map(paper->{
            paper.setUser(userRepository.getOne(paper.getStudyUserId()));
            return paper;
        });
    }

}

