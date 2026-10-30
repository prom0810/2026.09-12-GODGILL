package com.safewalk.global;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 진행 중인 트랜잭션이 커밋된 뒤에 실행할 작업을 등록한다. 트랜잭션이 없으면 바로 실행한다. 롤백되면 실행하지 않는다.
 * 예: DB 저장이 확정된 뒤에만 SSE·문자를 보내기.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
