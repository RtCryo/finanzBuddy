package com.finanzbuddy.api.auth.adapter.out.persistence;

import com.finanzbuddy.api.auth.port.out.TransactionRunnerPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SpringTransactionRunner implements TransactionRunnerPort {

    @Override
    @Transactional
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
