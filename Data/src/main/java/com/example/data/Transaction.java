package com.example.data;

import lombok.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

enum dbType {
    HOSPITAL_DB, HOSPITAL_ADMIN_DB
}

enum LockType {
    READ, WRITE, NONE
}

enum OperationType {
    INSERT, SELECT, UPDATE, DELETE
}

@FunctionalInterface
interface OperationCallback {
    void apply(String result);
}

class MedicalRecord {
    String id;
    String patientId;
    String notes;
    int accessCount;
}


@Getter
@Setter
@Builder
class Operation {
    public static String NEEDED_VALUE = "NEEDED_VALUE";
    public static String NEEDED_VALUE_2 = "NEEDED_2";

    OperationType operationType; // INSERT, SELECT, UPDATE, DELETE
    dbType dbType; // HOSPITAL_DB, HOSPITAL_ADMIN_DB
    String sql;  // SQL statement
    LockType lockType; // READ, WRITE, NONE - in case of insert
    PLLock lock; // Lock that this operation has
    RowIdentifier rowIdentifier;  // Identifier for the row this operation affects
    Transaction transaction;  // Transaction that this operation belongs to
    // Constructor, getters, setters
    // rollback SQL statement
    String result;
    String rollbackSql;
    Operation parentOperation;
    Operation parentOperation2;

    @Override
    public String toString() {
        return "Operation{" +
                "operationType=" + operationType +
                ", dbType=" + dbType +
                ", sql='" + sql + '\'' +
                ", lockType=" + lockType +
                ", rowIdentifier=" + rowIdentifier +
                ", result='" + result + '\'' +
                ", rollbackSql='" + rollbackSql + '\'' +
                '}';
    }

    void execute() {
        String result = transaction.transactionExecutor.executeOperation(this);
        this.result = result;
        for(Operation operation : transaction.getOperations()) {
            if(operation.parentOperation==this) {
                operation.setSql(operation.getSql().replace(NEEDED_VALUE, result));
                operation.rollbackSql = operation.rollbackSql.replace(NEEDED_VALUE, result);
                if(operation.rowIdentifier!=null){
                    operation.rowIdentifier.rowId = operation.rowIdentifier.rowId.replace(NEEDED_VALUE, result);
                }
            }
            if(operation.parentOperation2==this){
                operation.setSql(operation.getSql().replace(NEEDED_VALUE_2, result));
                operation.rollbackSql = operation.rollbackSql.replace(NEEDED_VALUE_2, result);
                if(operation.rowIdentifier!=null){
                    operation.rowIdentifier.rowId = operation.rowIdentifier.rowId.replace(NEEDED_VALUE_2, result);
                }

            }
        }
    }
    void rollback() {
        transaction.transactionExecutor.rollbackOperation(this);
    }
}

@Getter
@Setter
class Transaction {
    JdbcTemplate jdbcTemplateHospitalDB;
    JdbcTemplate jdbcTemplateHospitalAdminDB;

    SimpleJdbcInsert simpleJdbcInsertHospitalDB;
    SimpleJdbcInsert simpleJdbcInsertHospitalAdminDB;

    //timestamp
    LocalDateTime time = LocalDateTime.now();


    List<Operation> operations = new ArrayList<>();
    List<Operation> completedOperations = new ArrayList<>();
    Operation currentOperation;
    boolean isCommitted;
    boolean isRolledBack;
    TransactionExecutor transactionExecutor = new TransactionExecutor(this);

    @Override
    public String toString() {
        return "Transaction{" +
                "jdbcTemplateHospitalDB=" + jdbcTemplateHospitalDB +
                ", jdbcTemplateHospitalAdminDB=" + jdbcTemplateHospitalAdminDB +
                ", simpleJdbcInsertHospitalDB=" + simpleJdbcInsertHospitalDB +
                ", simpleJdbcInsertHospitalAdminDB=" + simpleJdbcInsertHospitalAdminDB +
                ", operations=" + operations +
                ", completedOperations=" + completedOperations +
                ", currentOperation=" + currentOperation +
                ", isCommitted=" + isCommitted +
                ", isRolledBack=" + isRolledBack +
                ", transactionExecutor=" + transactionExecutor +
                '}';
    }

    void addOperation(Operation operation) {
        operation.setTransaction(this);
        operations.add(operation);
    }

    void releaseLocks() {
        for(Operation operation : completedOperations) {
            if(operation.lock!=null){
                System.out.println("Freeing up lock for: " + operation.getRowIdentifier());
                operation.lock.freeUpLock(operation);
                operation.lock = null;
            }
        }
    }

    void rollback() {
        for(Operation operation : operations) {
            if(operation.lock!=null) {
                operation.rollback();
                operation.lock.freeUpLock(operation);
                operation.lock = null;
            }
        }
        completedOperations.clear();
    }

    JdbcTemplate getConnection(dbType db) {
        return switch (db) {
            case HOSPITAL_DB -> jdbcTemplateHospitalDB;
            case HOSPITAL_ADMIN_DB -> jdbcTemplateHospitalAdminDB;
        };
    }

    SimpleJdbcInsert getSimpleJdbcInsert(dbType db) {
        return switch (db) {
            case HOSPITAL_DB -> simpleJdbcInsertHospitalDB;
            case HOSPITAL_ADMIN_DB -> simpleJdbcInsertHospitalAdminDB;
        };
    }
}

//class to identify a row uniquely to lock on
@Data
@Builder
class RowIdentifier {
    String tableName;
    String rowId;
}

//create a class for Lock
//the Lock has the following fields:
//the LockType(either shared or exclusive) (read or write)
//the primary key of the row
//the table name
//the transaction that has the lock
//transactions that are waiting for the lock
@NoArgsConstructor
@Getter
@Setter
class PLLock {
    RowIdentifier rowId;
    List<Operation> operationsAcquired = new ArrayList<>(); //will be only one if lockType is "write"
    // , will be multiple if lockType is "read"
    PriorityQueue<Operation> operationsWaiting = new PriorityQueue<>(Comparator.comparing(o -> o.getTransaction().time));

    synchronized void freeUpLock(Operation operation) {
        synchronized (this) {
            operationsWaiting.remove(operation);
            operationsAcquired.remove(operation);
            if (operationsAcquired.isEmpty() && !operationsWaiting.isEmpty()) {
                operationsAcquired.add(operationsWaiting.remove());
                if (operationsAcquired.get(0).lockType == LockType.WRITE) {
                    return;
                }
                while (!operationsWaiting.isEmpty() && operationsWaiting.peek().getLockType() == LockType.READ) {
                    operationsAcquired.add(operationsWaiting.remove());
                }
            }
        }
    }
}

class WaitForGraph {
    Map<RowIdentifier, PLLock> locks = new ConcurrentHashMap<>();


    Thread cycleChecker = new Thread(() -> {
        while(true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            synchronized (this) {
//                System.out.println("Checking for cycles");
                if (isCycle()) {
                    System.out.println("Cycle detected");
                }
            }
        }
    });
    {
        cycleChecker.start();
    }

    //acquire lock
    synchronized void acquireLock(RowIdentifier rowId, Operation operation) {
        synchronized (this) {
            System.out.println("Acquiring lock for: " + rowId);
            PLLock lock = locks.getOrDefault(rowId, null);

            if (lock == null) {
                lock = new PLLock();
                lock.setRowId(rowId);
                lock.operationsAcquired.add(operation);
                operation.setLock(lock);
                locks.put(rowId, lock);
            } else {
                operation.setLock(lock);
                if (lock.operationsAcquired.stream().allMatch(op -> op.getTransaction().equals(operation.getTransaction()))) {
                    lock.operationsAcquired.add(operation);
                    //if successively acquired by the same transaction, return
                    return;
                }
                if (lock.operationsAcquired.isEmpty()) {
                    lock.operationsAcquired.add(operation);
                    return;
                }
                if (lock.getOperationsAcquired().get(0).lockType == LockType.READ) {
                    if (operation.getLockType() == LockType.READ) {
                        lock.operationsAcquired.add(operation);
                    } else {
                        lock.operationsWaiting.add(operation);
                    }
                } else {
                    lock.operationsWaiting.add(operation);
                }
            }
        }

    }

    synchronized boolean isCycle() {
        Set<Operation> visited = new HashSet<>();
        Set<Operation> recursionStack = new HashSet<>();

        for (PLLock lock : locks.values()) {
            for (Operation operation : lock.getOperationsWaiting()) {
                if (dfsAndResolve(operation, visited, recursionStack)) {
                    return true; // Cycle detected and resolved
                }
            }
        }
        return false; // No cycle detected
    }

    synchronized boolean dfsAndResolve(Operation operation, Set<Operation> visited, Set<Operation> recursionStack) {
        if (recursionStack.contains(operation)) {
            // Cycle detected, initiate rollback and resolution process for youngest transaction
            var lowestTime = operation.transaction.time;
            var youngest =  operation;
            for(Operation op: recursionStack) {
                if(op.transaction.time.isAfter(lowestTime)) {
                    lowestTime = op.transaction.time;
                    youngest = op;
                }
            }
            resolveDeadlock(youngest.getTransaction());
            return true;
        }

        if (visited.contains(operation)) {
            // If operation is already visited, skip processing
            return false;
        }

        // Add operation to visited and recursion stack sets
        visited.add(operation);
        recursionStack.add(operation);

        // Traverse through waiting operations
        PLLock lockHeld = locks.getOrDefault(operation.getRowIdentifier(), null);
        if (lockHeld != null) {
            for (Operation waitingOperation : lockHeld.getOperationsWaiting()) {
                if (dfsAndResolve(waitingOperation, visited, recursionStack)) {
                    return true; // Cycle detected in deeper call
                }
            }
        }

        // Remove operation from recursion stack
        recursionStack.remove(operation);
        return false;
    }

    private synchronized void resolveDeadlock(Transaction transaction) {
        // Remove the transaction from all locks
        System.out.println("Cycle detected for transaction: " + transaction);

        transaction.transactionExecutor.shouldRollback = true;
        transaction.rollback();

        synchronized (this) {
            for (PLLock lock : locks.values()) {
                lock.getOperationsWaiting().removeIf(op -> op.getTransaction().equals(transaction));
                lock.getOperationsAcquired().removeIf(op -> op.getTransaction().equals(transaction));
            }
        }

        // Rollback any committed operations of the transaction
    }
// Constructor, getters, setters
}

class TransactionExecutor implements Runnable {
    private final Transaction transaction;
    //static list of transactions concurrent
    static ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();

    static WaitForGraph waitForGraph = new WaitForGraph();

    boolean shouldRollback = false;
    public TransactionExecutor(Transaction transaction) {
        this.transaction = transaction;
    }

    @SneakyThrows
    @Override
    public void run() {
            transactions.add(transaction);
            //executeTransaction while return value is false
            while(!executeTransaction()) {
                //while executeTransaction returns false
                //wait
                Thread.sleep(500);
            }

    }
    private boolean executeTransaction() throws InterruptedException {
        // Execute each operation in the transaction
        for (Operation operation : transaction.getOperations()) {
            // Acquire lock for the operation
            if(operation.rowIdentifier!=null) {
                waitForGraph.acquireLock(operation.getRowIdentifier(), operation);


                while (!operation.lock.operationsAcquired.contains(operation)) {
                    //while the operation is not in the operationsAcquired list of the lock
                    //wait
                    Thread.sleep(1000);
                    if (shouldRollback) {
//                        transaction.rollback();
                        shouldRollback = false;
                        return false;
                    }
                }
            }
            // Execute the operation
            try {
                operation.execute();
            } catch (Exception e) {
                // Rollback the transaction if any operation fails
                transaction.rollback();
                throw e;
            }

            // Add to completed operations
            transaction.getCompletedOperations().add(operation);
        }

        // Commit the transaction if all operations are executed
        commitTransaction();
        transaction.releaseLocks();
        return true;
    }
    private void commitTransaction() {
        // Implement the commit logic for the transaction
        // This could involve finalizing the changes made by the transaction
        transaction.setCommitted(true);
    }

    String executeOperation(Operation operation) {
        System.out.println("Executing operation: " + operation.getSql());
//        if(operation.operationType==OperationType.INSERT) {
//            KeyHolder keyHolder = new GeneratedKeyHolder();
//            transaction.getConnection(operation.getDbType()).update(operation.getSql(), keyHolder);
//            operation.setRollbackSql("DELETE FROM " + operation.getRowIdentifier().getTableName() + " WHERE id = " + keyHolder.getKey());
//        }
//        else {
        if(operation.operationType==OperationType.SELECT) {
            String result = transaction.getConnection(operation.getDbType()).queryForObject(operation.getSql(), String.class);
            return result;

        }
            transaction.getConnection(operation.getDbType()).execute(operation.getSql());
//            operation.setRollbackSql("DELETE FROM " + operation.getRowIdentifier().getTableName() + " WHERE id = " + operation.getRowIdentifier().getRowId());
//        }
        return "Success";
    }
    void rollbackOperation(Operation operation) {
        System.out.println("Rolling back operation: " + operation.getRollbackSql());
        transaction.getConnection(operation.getDbType()).execute(operation.getRollbackSql());
    }
}
