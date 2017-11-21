
def cut_away_warmup_cooldown(requests, warmup_period_endtime, cooldown_period_starttime):
    return requests[(requests['initializeClockTime'] > warmup_period_endtime) & (requests['initializeClockTime'] < cooldown_period_starttime)]