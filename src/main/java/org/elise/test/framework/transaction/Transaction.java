package org.elise.test.framework.transaction;


/**
 * Created by huxuehan on 2016/10/18.
 */
public abstract class Transaction {
    public abstract void sendRequest() throws Throwable;

    public abstract long getIntervalTimeStamp() throws Throwable;

    public abstract Transaction getNextTransaction() throws Throwable;

    public abstract void setNextTransaction(Transaction nextTransaction) throws Throwable;
}
