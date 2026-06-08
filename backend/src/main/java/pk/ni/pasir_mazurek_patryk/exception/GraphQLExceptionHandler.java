package pk.ni.pasir_mazurek_patryk.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class GraphQLExceptionHandler implements DataFetcherExceptionResolver {

    @Override
    public @NonNull Mono<List<GraphQLError>> resolveException(
            @NonNull Throwable exception,
            @NonNull DataFetchingEnvironment env
    ) {
        if(exception instanceof ConstraintViolationException validationEx) {
            List<GraphQLError> errors = validationEx.getConstraintViolations().stream()
                    .map(violation -> GraphQLError.newError()
                            .message(violation.getMessage())
                            .path(env.getExecutionStepInfo().getPath())
                            .build())
                    .toList();
            return Mono.just(errors);
        }

        GraphQLError error = GraphqlErrorBuilder.newError(env)
                .message("Wystapil blad " + exception.getMessage())
                .build();
        return Mono.just(List.of(error));
    }
}
