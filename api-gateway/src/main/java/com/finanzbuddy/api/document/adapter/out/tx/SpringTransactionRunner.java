package com.finanzbuddy.api.document.adapter.out.tx;

import com.finanzbuddy.api.document.port.out.TransactionRunnerPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component("documentSpringTransactionRunner")
public class SpringTransactionRunner implements TransactionRunnerPort {

    private final TransactionTemplate txTemplate;

    public SpringTransactionRunner(PlatformTransactionManager txManager) {
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return txTemplate.execute(status -> work.get());
    }
}
