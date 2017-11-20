import pandas as pd

def create_window_statistics(metrics, windowsize):
    metrics['timestamp'] = pd.to_datetime(metrics['initializeClockTime'], unit='ms')

    mean_data = metrics.loc[:, ['timestamp', 'queueLength', 'responseTime_ms', 'queueTime_ms', 'workerServiceTime_ms']]
    count_data = metrics.loc[:, ['timestamp']]
    count_data['count'] = 1

    mean_window = mean_data.rolling(windowsize, on='timestamp').mean()
    count_window = count_data.rolling(windowsize, on='timestamp').count().drop('timestamp', axis=1)
    timekeeping = (metrics['initializeClockTime'] - metrics['initializeClockTime'].min()) / 1000
    return pd.concat([mean_window,count_window, timekeeping], axis=1)
