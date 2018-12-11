import csv
import os
import logging
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

log = logging.getLogger(__name__)

def collect_results(start_folder, fileName):
    log.info("Collecting timestamps into collect.csv\n")

    header = "Topology,device,duration\n"
    csvFile = open('collect.csv', 'w')
    csvFile.write(header)
    csvFileWriter=csv.writer(csvFile)

    for root, dirs, files in os.walk(start_folder):
        if fileName not in files:
            continue
        filepath = os.path.join(root,fileName)
        rootPath = root.rsplit('/',2)
        with open(filepath, 'r') as f:
            for line in f:
                    csvFileWriter.writerow([rootPath[1],rootPath[2],\
                                            int(line.split('register:',1)[1])])
    csvFile.close()


def plotResult(csvFile):
    log.info("Plotting mean time to register value\n")

    mean_duration = {}
    data = pd.read_csv(csvFile)

    # Group the data based on topology name
    grp_by_topo = data.groupby('Topology')

    for topo, topo_df in grp_by_topo:
        mean_time = topo_df['duration'].mean()
        mean_duration[topo]=mean_time

    # Plot values

    f = plt.figure()
    ax = f.add_subplot(111)

    ax.set_xlabel('Topology')
    ax.set_ylabel('Mean TT Register')
    ax.set_title('Time for first register')

    for key, val in mean_duration.items():
        ax.plot(key, val*1e+6,'bo')

    plt.show()

def plotBoxPlot(csvFile):
    log.info("Plotting box Plot for time to register\n")

    f = plt.figure()
    ax = f.add_subplot(111)

    coloumn = ['two', 'three', 'four', 'five', 'six', 'seven', 'eight']
    data = pd.read_csv(csvFile)
    data.boxplot(by="Topology", ax=ax)

    ax.set_ylabel("Time in MircorSeconds")
    plt.show()


if __name__ == "__main__":

    import argparse

    parser = argparse.ArgumentParser(description="Collect Results into a single CSV file")
    parser.add_argument('-sf', '--folder', type=str, help="Basefolder to collection of results", \
                        default='/home/basavaraj/ODL/Thesis/old_results/06_12_dec/results')

    args = parser.parse_args()

    logconf = {'format': '[%(asctime)s.%(msecs)-3d: %(name)-16s - %(levelname)-5s] %(message)s', 'datefmt': "%H:%M:%S"}
    logging.basicConfig(level=logging.DEBUG, **logconf)

    fileName = 'timestamp.txt'
    csvFile = 'collect.csv'

    log.info("Collecting Timestamps and plotting mean time to register\n")
    collect_results(args.folder, fileName)
    plotResult(csvFile)
    plotBoxPlot(csvFile)











