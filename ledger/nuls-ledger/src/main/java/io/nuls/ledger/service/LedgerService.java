package io.nuls.ledger.service;

import io.nuls.ledger.db.Repository;
import io.nuls.tools.core.annotation.Autowired;
import io.nuls.tools.core.annotation.Service;

import java.math.BigInteger;

/**
 * Created by wangkun23 on 2018/11/19.
 */
public interface LedgerService {


    /**
     * get user account balance
     *
     * @param address
     * @return
     */
    BigInteger getBalance(String address);
}
