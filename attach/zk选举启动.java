/**
 * 启动一轮新的选举。当状态变为looking时调用。
 */
public Vote lookForLeader() throws InterruptedException {
    try {
        HashMap<Long, Vote> recvset = new HashMap<Long, Vote>();

        HashMap<Long, Vote> outofelection = new HashMap<Long, Vote>();

        int notTimeout = finalizeWait;

        /**
         * 初始投票为自己
         */
        synchronized(this){
            logicalclock++;
            updateProposal(getInitId(), getInitLastLoggedZxid(), getPeerEpoch());
        }

        sendNotifications();

        while ((self.getPeerState() == ServerState.LOOKING) &&
                (!stop)){
            /**
             * 从接收队列中获取消息
             */
            Notification n = recvqueue.poll(notTimeout,
                    TimeUnit.MILLISECONDS);

            /**
             * 如果没有获取到新消息，而自己的发送队列已经全发送，认为是一轮投票结束，发送新的投票
             */
            if(n == null){
                if(manager.haveDelivered()){
                    sendNotifications();
                } else {
                    manager.connectAll();
                }

                int tmpTimeOut = notTimeout*2;
                notTimeout = (tmpTimeOut < maxNotificationInterval?
                        tmpTimeOut : maxNotificationInterval);
                LOG.info("Notification time out: " + notTimeout);
            } else if(self.getVotingView().containsKey(n.sid)) {
                
                switch (n.state) {
                case LOOKING:
                    /**
                     * 情况一：投票中的周期大于当前周期，说明当前投票已作废。清空本地的投票信息。
                     *    校验外来投票和自己投票，并将本地投票更新为更优先的投票。并发送新投票。
                     * 情况二：投票中的周期小于当前周期，说明外来投票作废，直接跳过。
                     * 情况三：投票中的周期等于当前周期，说明是同一轮投票。
                     *    校验外来投票和自己投票，并将本地投票更新为更优先的投票。并发送新投票。
                     */
                    if (n.electionEpoch > logicalclock) {
                        logicalclock = n.electionEpoch;
                        recvset.clear();
                        if(totalOrderPredicate(n.leader, n.zxid, n.peerEpoch,
                                getInitId(), getInitLastLoggedZxid(), getPeerEpoch())) {
                            updateProposal(n.leader, n.zxid, n.peerEpoch);
                        } else {
                            updateProposal(getInitId(),
                                    getInitLastLoggedZxid(),
                                    getPeerEpoch());
                        }
                        sendNotifications();
                    } else if (n.electionEpoch < logicalclock) {
                        if(LOG.isDebugEnabled()){
                            LOG.debug("Notification election epoch is smaller than logicalclock. n.electionEpoch = 0x"
                                    + Long.toHexString(n.electionEpoch)
                                    + ", logicalclock=0x" + Long.toHexString(logicalclock));
                        }
                        break;
                    } else if (totalOrderPredicate(n.leader, n.zxid, n.peerEpoch,
                            proposedLeader, proposedZxid, proposedEpoch)) {
                        updateProposal(n.leader, n.zxid, n.peerEpoch);
                        sendNotifications();
                    }

                    /**
                     * 将本次获取的选票暂存在recvset中。
                     */
                    recvset.put(n.sid, new Vote(n.leader, n.zxid, n.electionEpoch, n.peerEpoch));

                    if (termPredicate(recvset,
                            new Vote(proposedLeader, proposedZxid,
                                    logicalclock, proposedEpoch))) {

                        // Verify if there is any change in the proposed leader
                        while((n = recvqueue.poll(finalizeWait,
                                TimeUnit.MILLISECONDS)) != null){
                            if(totalOrderPredicate(n.leader, n.zxid, n.peerEpoch,
                                    proposedLeader, proposedZxid, proposedEpoch)){
                                recvqueue.put(n);
                                break;
                            }
                        }

                        /*
                            * This predicate is true once we don't read any new
                            * relevant message from the reception queue
                            */
                        if (n == null) {
                            self.setPeerState((proposedLeader == self.getId()) ?
                                    ServerState.LEADING: learningState());

                            Vote endVote = new Vote(proposedLeader,
                                                    proposedZxid,
                                                    logicalclock,
                                                    proposedEpoch);
                            leaveInstance(endVote);
                            return endVote;
                        }
                    }
                    break;
                case OBSERVING:
                    LOG.debug("Notification from observer: " + n.sid);
                    break;
                case FOLLOWING:
                case LEADING:
                    /*
                        * Consider all notifications from the same epoch
                        * together.
                        */
                    if(n.electionEpoch == logicalclock){
                        recvset.put(n.sid, new Vote(n.leader,
                                                        n.zxid,
                                                        n.electionEpoch,
                                                        n.peerEpoch));
                        
                        if(ooePredicate(recvset, outofelection, n)) {
                            self.setPeerState((n.leader == self.getId()) ?
                                    ServerState.LEADING: learningState());

                            Vote endVote = new Vote(n.leader, 
                                    n.zxid, 
                                    n.electionEpoch, 
                                    n.peerEpoch);
                            leaveInstance(endVote);
                            return endVote;
                        }
                    }

                    /*
                        * Before joining an established ensemble, verify
                        * a majority is following the same leader.
                        */
                    outofelection.put(n.sid, new Vote(n.version,
                                                        n.leader,
                                                        n.zxid,
                                                        n.electionEpoch,
                                                        n.peerEpoch,
                                                        n.state));
        
                    if(ooePredicate(outofelection, outofelection, n)) {
                        synchronized(this){
                            logicalclock = n.electionEpoch;
                            self.setPeerState((n.leader == self.getId()) ?
                                    ServerState.LEADING: learningState());
                        }
                        Vote endVote = new Vote(n.leader,
                                                n.zxid,
                                                n.electionEpoch,
                                                n.peerEpoch);
                        leaveInstance(endVote);
                        return endVote;
                    }
                    break;
                default:
                    LOG.warn("Notification state unrecognized: {} (n.state), {} (n.sid)",
                            n.state, n.sid);
                    break;
                }
            } else {
                LOG.warn("Ignoring notification from non-cluster member " + n.sid);
            }
        }
        return null;
    }
}