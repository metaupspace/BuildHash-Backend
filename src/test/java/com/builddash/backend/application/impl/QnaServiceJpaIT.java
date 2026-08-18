package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.AnswerSource;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Answer;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.Question;
import com.builddash.backend.domain.model.QuestionThread;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.AnswerRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.QuestionRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards QnaServiceImpl.listThreads against an N+1 regression: AnswerRepository.findByQuestionIdIn
 * must be called once as a real batch fetch, never once per question in a loop. A per-question
 * loop is functionally invisible in a small test with a handful of questions — it only shows up
 * as a query-count regression, which is exactly what this test pins down.
 */
class QnaServiceJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private QnaServiceImpl qnaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UUID saveProduct() {
        Category category = new Category();
        category.setName("Cement");
        category.setSlug("cement-" + UUID.randomUUID());
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Product");
        product.setSlug("product-" + UUID.randomUUID());
        product.setCategoryId(savedCategory.getId());
        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(product).getId();
    }

    private UUID saveUser() {
        User user = new User();
        user.setPhone("+91" + (9000000000L + (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100000000L)));
        return userRepository.save(user).getId();
    }

    @Test
    void listThreads_withMultipleQuestionsAndAnswers_batchFetchesAnswersInOneQuery() {
        UUID productId = saveProduct();
        UUID userId = saveUser();

        for (int i = 0; i < 5; i++) {
            Question question = new Question();
            question.setProductId(productId);
            question.setUserId(userId);
            question.setBody("Question " + i);
            Question savedQuestion = questionRepository.save(question);

            Answer answer = new Answer();
            answer.setQuestionId(savedQuestion.getId());
            answer.setUserId(userId);
            answer.setBody("Answer to " + i);
            answer.setSource(AnswerSource.CUSTOMER);
            answerRepository.save(answer);
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<QuestionThread> threads = qnaService.listThreads(productId);

        // Exactly 2 statements: one SELECT for the questions, one batched SELECT ... IN (...) for
        // their answers. If this ever grows with the number of questions, findByQuestionIdIn is
        // being called per-question instead of once — the N+1 this test exists to catch.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

        assertThat(threads).hasSize(5);
        assertThat(threads).allSatisfy(thread -> assertThat(thread.answers()).hasSize(1));
    }
}
