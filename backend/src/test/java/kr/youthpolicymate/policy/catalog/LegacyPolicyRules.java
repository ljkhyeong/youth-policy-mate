package kr.youthpolicymate.policy.catalog;

import java.lang.reflect.*;
import java.time.*;
import java.util.*;

/** 데이터 이전 검증에만 사용하는 변경 전 구현. 운영 코드에서 참조하지 않는다. */
final class LegacyPolicyRules {
    static final Map<String, List<String>> DEPENDENCIES = Map.of(
        "KPassRules", List.of("age", "registration", "residence", "monthlyRides"),
        "YouthHousingSavingsRules", List.of("age", "homeOwnership", "incomeBasis,incomeAmount"),
        "SeoulYouthNetworkRules", List.of("birthRange", "seoulConnection", "consecutiveTerms", "priorDisqualification"),
        "MovingFeeRules", List.of("birthRange", "move", "contract", "homeOwnership", "housingCost", "income", "seoulSupport,otherSupport,requestedCost", "parentRental", "benefitReceipt", "excludedResidency"),
        "YouthTomorrowSavingsRules", List.of("birthRange", "workType,monthlyIncome", "householdIncome", "duplicateParticipation"),
        "GuaranteeFeeRules", List.of("guarantee", "deposit", "homeOwnership", "applicantType,incomeBasis,annualIncome"),
        "HaetsalronYouthRules", List.of("age", "applicantType", "incomeBasis,annualIncome", "lifetimeLimit"),
        "MisoYouthFutureRules", List.of("age", "employment", "credit,welfare,earnedIncomeCredit"),
        "FutureYouthJobsRules", List.of("birthRange", "residence", "employment", "education", "business", "publicJob")
    );
    static final List<Class<?>> TYPES = List.of(ExamFeeRules.class, WorkStudyRules.class, KPassRules.class,
            YouthHousingSavingsRules.class, SeoulYouthNetworkRules.class, MovingFeeRules.class, YouthTomorrowSavingsRules.class,
            GuaranteeFeeRules.class, HaetsalronYouthRules.class, MisoYouthFutureRules.class, FutureYouthJobsRules.class);
    static final Map<String, Class<?>> TYPES_BY_NUMBER = new LinkedHashMap<>();
    static { for (var type : TYPES) TYPES_BY_NUMBER.put(constant(type, "NUMBER"), type); }
    static String constant(Class<?> type, String field) {
        try { return (String) type.getField(field).get(null); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    static PolicyQuestions.Questionnaire questions(String number, Instant now) {
        var type = TYPES_BY_NUMBER.get(number);
        try { return invoke(type.getDeclaredMethod("questionnaire", long.class, Instant.class), 1L, now); }
        catch (NoSuchMethodException ignored) {
            try { return invoke(type.getDeclaredMethod("questionnaire", long.class), 1L); }
            catch (NoSuchMethodException failure) { throw new IllegalStateException(failure); }
        }
    }
    static PolicyQuestions.Evaluation evaluate(String number, PolicyQuestions.Request input, Instant now) {
        try { return invoke(TYPES_BY_NUMBER.get(number).getDeclaredMethod("evaluate", long.class, PolicyQuestions.Request.class, Instant.class), input.revision(), input, now); }
        catch (NoSuchMethodException failure) { throw new IllegalStateException(failure); }
    }
    static PolicyQuestions.Check age(String number, LocalDate birth, Instant now) {
        var type = TYPES_BY_NUMBER.get(number);
        try { return invoke(type.getDeclaredMethod("ageCheck", LocalDate.class, Instant.class), birth, now); }
        catch (NoSuchMethodException ignored) {
            try { return invoke(type.getDeclaredMethod("ageCheck", LocalDate.class), birth); }
            catch (NoSuchMethodException failure) { throw new IllegalStateException(failure); }
        }
    }
    @SuppressWarnings("unchecked")
    private static <T> T invoke(Method method, Object... args) {
        try { return (T) method.invoke(null, args); }
        catch (InvocationTargetException failure) { throw (RuntimeException) failure.getCause(); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
