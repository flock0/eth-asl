
def cut_away_warmup_cooldown(requests, timestepName, warmup_period_endtime, cooldown_period_starttime):
    return requests[(requests[timestepName] > warmup_period_endtime) & (requests[timestepName] < cooldown_period_starttime)]