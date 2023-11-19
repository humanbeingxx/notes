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

                    /**
                     * 判定本次选举是否已经达成一致。并不需要等到所有选票都到达，只需要有达到一半以上的相同投票。
                     */
                    if (termPredicate(recvset,
                            new Vote(proposedLeader, proposedZxid,
                                    logicalclock, proposedEpoch))) {

                        /**
                         * 再验证一下是否有新选票比当前赢得选取的更合适。
                         * 如果有，则放回接收队列，重新下一轮投票。
                         */
                        while((n = recvqueue.poll(finalizeWait,
                                TimeUnit.MILLISECONDS)) != null){
                            if(totalOrderPredicate(n.leader, n.zxid, n.peerEpoch,
                                    proposedLeader, proposedZxid, proposedEpoch)){
                                recvqueue.put(n);
                                break;
                            }
                        }

                        /**
                         * 如果没有新选票，则投票完成。
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
                    /**
                     * 如果是用一个选举周期，且leader验证通过，则认同并退出选举
                     */
                    if(n.electionEpoch == logicalclock){

                        // 继续放到已收到的投票中，因为此时可能收到的投票还不足以证明外来投票的leader就是leader
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

                    /**
                     * outofelection中保存的是heading和following选票。
                     * 如果选举周期不一致或者上面的leading校验没通过，会在outofelection中校验leading的有效性。
                     * 如果当前的本地投票周期落后，则更新为外来leading，这个逻辑没问题，但如果本地投票领先呢?
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