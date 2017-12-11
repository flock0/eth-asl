
def cut_away_warmup_cooldown(requests, warmup_period_endtime, cooldown_period_starttime):
    return requests.loc[(requests.index >= warmup_period_endtime) & (requests.index <= cooldown_period_starttime)]