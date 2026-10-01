package com.finanzbuddy.api.auth.port.out;

import java.util.function.Supplier;

public interface TransactionRunnerPort {

    <T> T inTransaction(Supplier<T> work);
}
