
class MaxXputKeeper:
    mw_max_xput = 0
    mw_max_avg_resptime = 0
    mw_max_avg_queuetime = 0
    mw_max_missrate = 0
    mw_max_vc = 0
    mw_max_worker = 0
    mw_max_rep = 0

    mt_max_xput = 0
    mt_max_avg_resptime = 0
    mt_max_missrate = 0
    mt_max_vc = 0
    mt_max_worker = 0
    mt_max_rep = 0

    def tryset_mw_max(self, xput, avg_resptime, avg_queuetime, missrate, vc, worker, rep):
        if xput > self.mw_max_xput:
            self.mw_max_xput = xput
            self.mw_max_avg_resptime = avg_resptime
            self.mw_max_avg_queuetime = avg_queuetime
            self.mw_max_missrate = missrate
            self.mw_max_vc = vc
            self.mw_max_worker = worker
            self.mw_max_rep = rep

    def tryset_mt_max(self, xput, avg_resptime, missrate, vc, worker, rep):
        if xput > self.mt_max_xput:
            self.mt_max_xput = xput
            self.mt_max_avg_resptime = avg_resptime
            self.mt_max_missrate = missrate
            self.mw_max_vc = vc
            self.mw_max_worker = worker
            self.mw_max_rep = rep

    def print(self):
        print("Maximum observed middleware throughput: {}".format(self.mw_max_xput))
        print("Avg Responsetime: {}".format(self.mw_max_avg_resptime))
        print("Avg Queuetime: {}".format(self.mw_max_avg_queuetime))
        print("Miss rate: {}".format(self.mw_max_missrate))
        print("Configuration: {} VC, {} workers, rep {}\n".format(self.mw_max_vc, self.mw_max_worker, self.mw_max_rep))

        print("Maximum observed memtier throughput: {}".format(self.mt_max_xput))
        print("Avg Responsetime: {}".format(self.mt_max_avg_resptime))
        print("Miss rate: {}".format(self.mt_max_missrate))
        print("Configuration: {} VC, {} workers, rep {}".format(self.mt_max_vc, self.mt_max_worker, self.mt_max_rep))