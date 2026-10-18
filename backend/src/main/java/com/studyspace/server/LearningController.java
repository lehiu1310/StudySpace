package com.studyspace.server;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
class LearningController {
    final JdbcTemplate db;
    final AuthService auth;
    LearningController(JdbcTemplate db, AuthService auth) { this.db=db; this.auth=auth; }
    static long number(Object value) { return ((Number)value).longValue(); }
    Map<String,Object> one(String sql,Object... args) {
        var rows=db.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy nội dung.");
        return rows.get(0);
    }
    void chapter(long id) {
        one("SELECT ch.id FROM chapters ch JOIN courses c ON c.id=ch.course_id WHERE ch.id=? AND c.published=TRUE",id);
    }
    @GetMapping("/courses")
    List<Map<String,Object>> courses(@RequestHeader(value="Authorization",required=false) String token) {
        var a=auth.require(token);
        return db.queryForList("SELECT c.*, (SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=c.id) AS lesson_count, (SELECT COUNT(*) FROM lesson_progress p JOIN lessons l ON l.id=p.lesson_id JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=c.id AND p.user_id=?) AS completed_count, (SELECT cl.started_at FROM course_learning cl WHERE cl.course_id=c.id AND cl.user_id=?) AS started_at FROM courses c WHERE c.published=TRUE ORDER BY c.id",a.id(),a.id());
    }
    @PutMapping("/courses/{id}/start")
    @Transactional
    Map<String,Long> startCourse(@PathVariable long id,@RequestHeader("Authorization") String token) {
        var a=auth.require(token);
        if(!a.role().equals("STUDENT")) throw new ResponseStatusException(FORBIDDEN,"Chỉ học viên mới có thể bắt đầu khóa học.");
        one("SELECT id FROM courses WHERE id=? AND published=TRUE",id);
        one("SELECT id FROM accounts WHERE id=? FOR UPDATE",a.id());
        long now=System.currentTimeMillis();
        int updated=db.update("UPDATE course_learning SET last_accessed_at=? WHERE user_id=? AND course_id=?",now,a.id(),id);
        if(updated==0)db.update("INSERT INTO course_learning(user_id,course_id,started_at,last_accessed_at) VALUES(?,?,?,?)",a.id(),id,now,now);
        return Map.of("startedAt",db.queryForObject("SELECT started_at FROM course_learning WHERE user_id=? AND course_id=?",Long.class,a.id(),id));
    }
    @GetMapping("/courses/{id}")
    Map<String,Object> course(@PathVariable long id,@RequestHeader("Authorization") String token) {
        var a=auth.require(token);
        var course=one("SELECT * FROM courses WHERE id=? AND published=TRUE",id);
        var chapters=db.queryForList("SELECT * FROM chapters WHERE course_id=? ORDER BY position,id",id);
        boolean blockedByPreviousLesson=false;
        for(var ch:chapters) {
            var lessons=db.queryForList("SELECT l.*, CASE WHEN p.lesson_id IS NULL THEN FALSE ELSE TRUE END AS completed FROM lessons l LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.user_id=? WHERE l.chapter_id=? ORDER BY l.position,l.id",a.id(),ch.get("id"));
            for(var lesson:lessons) {
                boolean completed=Boolean.TRUE.equals(lesson.get("completed"));
                lesson.put("locked",blockedByPreviousLesson&&!completed);
                if(!completed)blockedByPreviousLesson=true;
            }
            ch.put("lessons",lessons);
            ch.put("question_count",db.queryForObject("SELECT COUNT(*) FROM questions WHERE chapter_id=? AND active=TRUE",Integer.class,ch.get("id")));
        }
        course.put("chapters",chapters); return course;
    }
    @PutMapping("/lessons/{id}/complete")
    @Transactional
    Map<String,Boolean> complete(@PathVariable long id,@RequestHeader("Authorization") String token) {
        var a=auth.require(token);
        var target=one("SELECT l.id,l.position,ch.id AS chapter_id,ch.position AS chapter_position,ch.course_id FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id JOIN courses c ON c.id=ch.course_id WHERE l.id=? AND c.published=TRUE",id);
        // Serialize repeated taps per account without deleting historical progress.
        one("SELECT id FROM accounts WHERE id=? FOR UPDATE",a.id());
        var ordered=db.queryForList("SELECT l.id,CASE WHEN p.lesson_id IS NULL THEN FALSE ELSE TRUE END AS completed FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.user_id=? WHERE ch.course_id=? ORDER BY ch.position,ch.id,l.position,l.id",a.id(),target.get("course_id"));
        for(var previous:ordered) {
            long previousId=number(previous.get("id"));
            if(previousId==id)break;
            if(!Boolean.TRUE.equals(previous.get("completed")))
                throw new ResponseStatusException(CONFLICT,"Hãy hoàn thành các bài học trước theo thứ tự.");
        }
        if(db.queryForObject("SELECT COUNT(*) FROM lesson_progress WHERE user_id=? AND lesson_id=?",Integer.class,a.id(),id)==0)
            db.update("INSERT INTO lesson_progress(user_id,lesson_id,completed_at) VALUES(?,?,?)",a.id(),id,System.currentTimeMillis());
        return Map.of("ok",true);
    }
    @GetMapping("/lessons/{id}/location")
    Map<String,Object> lessonLocation(@PathVariable long id,@RequestHeader("Authorization") String token) {
        auth.require(token);
        return one("SELECT ch.course_id FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id JOIN courses c ON c.id=ch.course_id WHERE l.id=? AND c.published=TRUE",id);
    }
    @PostMapping("/chapters/{id}/attempts")
    @Transactional
    Map<String,Object> start(@PathVariable long id,@RequestParam(defaultValue="false") boolean review,@RequestHeader("Authorization") String token) {
        var a=auth.require(token); chapter(id);
        one("SELECT id FROM accounts WHERE id=? FOR UPDATE",a.id());
        var pending=db.queryForList("SELECT id FROM quiz_attempts WHERE user_id=? AND chapter_id=? AND submitted_at IS NULL ORDER BY started_at DESC",a.id(),id);
        if(!pending.isEmpty()) return attempt(number(pending.get(0).get("id")),token);
        var recentScores=db.queryForList("SELECT score,total FROM quiz_attempts WHERE user_id=? AND chapter_id=? AND submitted_at IS NOT NULL ORDER BY submitted_at DESC LIMIT 3",a.id(),id);
        Double recentMastery=recentScores.isEmpty()?null:recentScores.stream()
            .mapToDouble(row->100.0*number(row.get("score"))/number(row.get("total"))).average().orElse(0);
        int targetDifficulty=AdaptiveQuizPolicy.targetDifficulty(recentMastery);
        var questions=db.queryForList("SELECT * FROM questions WHERE chapter_id=? AND active=TRUE",id);
        Collections.shuffle(questions);
        var history=new HashMap<Long,long[]>();
        for(var row:db.queryForList("SELECT aa.question_id,COUNT(*) AS attempts,SUM(CASE WHEN aa.selected_option IS NULL OR aa.selected_option<>aa.correct_option THEN 1 ELSE 0 END) AS incorrect FROM attempt_answers aa JOIN quiz_attempts qa ON qa.id=aa.attempt_id WHERE qa.user_id=? AND qa.chapter_id=? AND qa.submitted_at IS NOT NULL GROUP BY aa.question_id",a.id(),id))
            history.put(number(row.get("question_id")),new long[]{number(row.get("attempts")),number(row.get("incorrect"))});
        if(review) {
            var wrong=db.queryForList("SELECT DISTINCT aa.question_id FROM attempt_answers aa JOIN quiz_attempts qa ON qa.id=aa.attempt_id WHERE qa.user_id=? AND qa.chapter_id=? AND qa.submitted_at IS NOT NULL AND (aa.selected_option IS NULL OR aa.selected_option<>aa.correct_option)",a.id(),id);
            Set<Long> ids=new HashSet<>(); wrong.forEach(row->ids.add(number(row.get("question_id"))));
            questions.sort(Comparator.<Map<String,Object>>comparingInt(q->ids.contains(number(q.get("id")))?0:1)
                .thenComparingDouble(q->priority(q,targetDifficulty,history)));
        } else questions.sort(Comparator.comparingDouble(q->priority(q,targetDifficulty,history)));
        questions=new ArrayList<>(questions.subList(0,Math.min(10,questions.size())));
        if(questions.isEmpty()) throw new ResponseStatusException(CONFLICT,"Chương chưa có câu hỏi.");
        long now=System.currentTimeMillis();
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        int total=questions.size();
        db.update(con->{var ps=con.prepareStatement("INSERT INTO quiz_attempts(user_id,chapter_id,started_at,deadline,total) VALUES(?,?,?,?,?)",new String[]{"id"}); ps.setLong(1,a.id());ps.setLong(2,id);ps.setLong(3,now);ps.setLong(4,now+total*90000L);ps.setInt(5,total);return ps;},key);
        long attemptId=Objects.requireNonNull(key.getKey()).longValue();
        int position=0;
        for(var q:questions) db.update("INSERT INTO attempt_answers(attempt_id,question_id,position,prompt,option_a,option_b,option_c,option_d,correct_option,explanation) VALUES(?,?,?,?,?,?,?,?,?,?)",attemptId,q.get("id"),position++,q.get("prompt"),q.get("option_a"),q.get("option_b"),q.get("option_c"),q.get("option_d"),q.get("correct_option"),q.get("explanation"));
        return attempt(attemptId,token);
    }
    private double priority(Map<String,Object> question,int targetDifficulty,Map<Long,long[]> history) {
        long[] stats=history.getOrDefault(number(question.get("id")),new long[]{0,0});
        return AdaptiveQuizPolicy.priority(targetDifficulty,((Number)question.get("difficulty")).intValue(),stats[0],stats[1]);
    }
    @GetMapping("/attempts/{id}")
    Map<String,Object> attempt(@PathVariable long id,@RequestHeader("Authorization") String token) {
        var a=auth.require(token);
        var result=one("SELECT * FROM quiz_attempts WHERE id=? AND user_id=?",id,a.id());
        String columns=result.get("submitted_at")==null ? "question_id,position,prompt,option_a,option_b,option_c,option_d,selected_option" : "question_id,position,prompt,option_a,option_b,option_c,option_d,selected_option,correct_option,explanation";
        result.put("questions",db.queryForList("SELECT "+columns+" FROM attempt_answers WHERE attempt_id=? ORDER BY position",id));
        result.put("server_now",System.currentTimeMillis());
        return result;
    }
    record Answers(Map<String,Integer> answers) {}
    record AiGeneratedQuestion(String prompt, List<String> options, int correctOption, String explanation) {}

    @Transactional
    Map<String,Object> createAiReviewAttempt(String token, long chapterId, List<AiGeneratedQuestion> questions) {
        var account = auth.require(token);
        chapter(chapterId);
        if (questions.size() != 5) throw new ResponseStatusException(BAD_GATEWAY, "AI trả về số câu hỏi không hợp lệ.");
        one("SELECT id FROM accounts WHERE id=? FOR UPDATE", account.id());
        long now = System.currentTimeMillis();
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO quiz_attempts(user_id,chapter_id,started_at,deadline,total) VALUES(?,?,?,?,?)",
                new String[]{"id"});
            statement.setLong(1, account.id());
            statement.setLong(2, chapterId);
            statement.setLong(3, now);
            statement.setLong(4, now + questions.size() * 90000L);
            statement.setInt(5, questions.size());
            return statement;
        }, key);
        long attemptId = Objects.requireNonNull(key.getKey()).longValue();
        for (int i = 0; i < questions.size(); i++) {
            var question = questions.get(i);
            var questionKey = new org.springframework.jdbc.support.GeneratedKeyHolder();
            db.update(connection -> {
                var statement = connection.prepareStatement(
                    "INSERT INTO questions(chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty,active) VALUES(?,?,?,?,?,?,?,?,2,FALSE)",
                    new String[]{"id"});
                statement.setLong(1, chapterId);
                statement.setString(2, question.prompt());
                statement.setString(3, question.options().get(0));
                statement.setString(4, question.options().get(1));
                statement.setString(5, question.options().get(2));
                statement.setString(6, question.options().get(3));
                statement.setInt(7, question.correctOption());
                statement.setString(8, question.explanation());
                return statement;
            }, questionKey);
            long questionId = Objects.requireNonNull(questionKey.getKey()).longValue();
            db.update("INSERT INTO attempt_answers(attempt_id,question_id,position,prompt,option_a,option_b,option_c,option_d,correct_option,explanation) VALUES(?,?,?,?,?,?,?,?,?,?)",
                attemptId, questionId, i, question.prompt(), question.options().get(0), question.options().get(1),
                question.options().get(2), question.options().get(3), question.correctOption(), question.explanation());
        }
        return attempt(attemptId, token);
    }

    @PutMapping("/attempts/{id}/answers")
    @Transactional
    Map<String,Object> save(@PathVariable long id,@RequestBody Answers body,@RequestHeader("Authorization") String token) {
        var a=auth.require(token); var row=one("SELECT * FROM quiz_attempts WHERE id=? AND user_id=? FOR UPDATE",id,a.id());
        if(row.get("submitted_at")!=null) return attempt(id,token);
        if(System.currentTimeMillis()>number(row.get("deadline"))) throw new ResponseStatusException(CONFLICT,"Đã hết giờ. Hãy nộp bài với đáp án đã lưu.");
        if(body.answers()==null || body.answers().size()>10) throw new IllegalArgumentException();
        for(var entry:body.answers().entrySet()) {
            if(entry.getValue()==null || entry.getValue()<0 || entry.getValue()>3) throw new IllegalArgumentException();
            if(db.update("UPDATE attempt_answers SET selected_option=? WHERE attempt_id=? AND question_id=?",entry.getValue(),id,Long.parseLong(entry.getKey()))!=1) throw new IllegalArgumentException();
        }
        return attempt(id,token);
    }
    @PostMapping("/attempts/{id}/submit")
    @Transactional
    Map<String,Object> submit(@PathVariable long id,@RequestHeader("Authorization") String token) {
        var a=auth.require(token); var row=one("SELECT * FROM quiz_attempts WHERE id=? AND user_id=? FOR UPDATE",id,a.id());
        if(row.get("submitted_at")==null) {
            int correct=db.queryForObject("SELECT COUNT(*) FROM attempt_answers WHERE attempt_id=? AND selected_option=correct_option",Integer.class,id);
            db.update("UPDATE quiz_attempts SET score=?,submitted_at=? WHERE id=?",correct,System.currentTimeMillis(),id);
        }
        return attempt(id,token);
    }
    @GetMapping("/progress")
    Map<String,Object> progress(@RequestHeader("Authorization") String token) {
        var a=auth.require(token);
        var history=db.queryForList("SELECT qa.*,ch.title AS chapter_title FROM quiz_attempts qa JOIN chapters ch ON ch.id=qa.chapter_id WHERE qa.user_id=? ORDER BY qa.started_at DESC",a.id());
        var mastery=db.queryForList("SELECT ch.id,ch.title, COUNT(qa.id) AS attempts, AVG(100.0*qa.score/qa.total) AS percentage FROM chapters ch JOIN courses c ON c.id=ch.course_id LEFT JOIN quiz_attempts qa ON qa.chapter_id=ch.id AND qa.user_id=? AND qa.submitted_at IS NOT NULL WHERE c.published=TRUE GROUP BY ch.id,ch.title ORDER BY ch.id",a.id());
        var lessonActivity=db.queryForList("SELECT completed_at FROM lesson_progress WHERE user_id=? ORDER BY completed_at DESC",a.id());
        return Map.of("history",history,"mastery",mastery,"courses",courses(token),"lessonActivity",lessonActivity);
    }
}
