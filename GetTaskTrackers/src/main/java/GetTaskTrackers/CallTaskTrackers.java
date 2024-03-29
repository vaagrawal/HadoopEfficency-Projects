package GetTaskTrackers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.Counters;
import org.apache.hadoop.mapred.Counters.Group;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.Task.Counter;
import org.apache.hadoop.mapred.TaskAttemptID;
import org.apache.hadoop.mapred.TaskCompletionEvent;
import org.apache.hadoop.mapred.TaskID;
import org.apache.hadoop.mapred.TaskReport;
import org.apache.hadoop.mapred.jobcontrol.Job;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class CallTaskTrackers implements Tool{

	public static String jobID;
	public static int killratio;
	public static boolean killtask;
	public Double maxprogress=0.0;
	HashMap<TaskID,TaskReport> tasks = new HashMap<TaskID, TaskReport>();
	public Configuration getConf() {
		// TODO Auto-generated method stub
		Configuration config = new Configuration();
		return config;
	}

	public void setConf(Configuration arg0) {
		// TODO Auto-generated method stub

	}
	public int run(String[] args) throws Exception {
		// TODO Auto-generated method stub
		jobID = args[0];
		killratio= Integer.parseInt(args[1]);
		killtask = args[2].equals("true");
		JobClient theJobClient = new JobClient(new InetSocketAddress("10.2.0.94", 8088), new Configuration());

		@SuppressWarnings("deprecation")

		RunningJob theJob = theJobClient.getJob(jobID); // caution, deprecated

		//verify if the job is running or not.
		if(theJob.isComplete()){
			System.out.println("The Job is already Complete!! Exiting");
			return 0;
		}
		JobID j_id = theJob.getID();
		//Check Current Running tasks:		
		TaskCompletionEvent[] taskcompletionevents = theJob.getTaskCompletionEvents(0);
		int num_tasks = taskcompletionevents.length;
		if(num_tasks==0){
			System.out.println("There are no tasks running atm!!");
		}
		TaskReport[] mt = theJobClient.getMapTaskReports(j_id);
		//Populate the Task's hash Map with the map and reduce tasks
		for (TaskReport trep : mt){
			if(!tasks.containsKey(trep.getTaskID())){
				tasks.put(trep.getTaskID(), trep);
			}
		}
		TaskReport[] rt = theJobClient.getReduceTaskReports(j_id);
		for (TaskReport trep : rt){
			if(!tasks.containsKey(trep.getTaskID())){
				tasks.put(trep.getTaskID(), trep);
			}
		}
		//Check the Task details
		//		for(Entry<TaskID, TaskReport> t: tasks.entrySet()){
		//		printTaskDetails(t.getValue());
		//}
		//Send only those tasks that are ether completed or running as an optimization
		TaskID fastesttaskID = fastesttaskID(tasks);
		ArrayList<TaskID> slowTask = findSlowTasks(tasks, maxprogress,killratio);
		if(!killtask){
			System.out.println("TPrinting Task details that are slow:");
			for(TaskID sT: slowTask){
				for(Entry<TaskID, TaskReport> t: tasks.entrySet()){
					if(t.getKey()==sT)
						printTaskDetails(t.getValue());
				}	
			}
		}
		else{
			System.out.println("Selected to Kill the Slow Tasks");
			for(TaskID sTk: slowTask){
				for(Entry<TaskID, TaskReport> t: tasks.entrySet()){
					if(t.getKey()==sTk){
						System.out.println("Inside if   -< Checking: "+ sTk.toString());
						//Running Task Attempts is returning a collection with no data.!!
						//						Collection<TaskAttemptID> ta = t.getValue().getRunningTaskAttempts();
						for(int i=0;i<4;i++){
							TaskAttemptID attemptid = new TaskAttemptID(sTk, i);
							//						Iterator<TaskAttemptID> i = ta.iterator();
							//						while(i.hasNext()){
							if(t.getValue().getState().equals("RUNNING")){
								System.out.println("Killing attempt: " + attemptid.toString());
								killTask(attemptid);
							}
						}
					}
				}
			}	
		}
		return 0;
	}
	public void killTask(TaskAttemptID t){
		String s = null;
		try {
			String change_user = "su hdfs";
			String killcommand = "mapred job -kill-task ";
			killcommand = killcommand.concat(t.toString());
			System.out.println("The Command: "+ killcommand);
			//Function taken from: http://alvinalexander.com/java/edu/pj/pj010016
			Process userchange = Runtime.getRuntime().exec(change_user);
			userchange.waitFor();
			Process p = Runtime.getRuntime().exec(killcommand);
			p.waitFor();
//Print the error Logs:
			
			BufferedReader stdInput = new BufferedReader(new
					InputStreamReader(p.getInputStream()));

			BufferedReader stdError = new BufferedReader(new
					InputStreamReader(p.getErrorStream()));

			// read the output from the command
			//	System.out.println("Here is the standard output of the command:\n");
			while ((s = stdInput.readLine()) != null) {
//				System.out.println(s);
			}

			// read any errors from the attempted command
			//		System.out.println("Here is the standard error of the command (if any):\n");
			while ((s = stdError.readLine()) != null) {
				System.out.println("Error Message : "+s);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public TaskID fastesttaskID(HashMap<TaskID, TaskReport> tasks){
		TaskID fastestTask = new TaskID();

		Double currentprogress=0.0;
		Long finalTime = (long) 10000000;
		for(Entry<TaskID,TaskReport> t: tasks.entrySet()){
			if(t.getValue().getState().equals("SUCCEEDED")){

				finalTime = t.getValue().getFinishTime();
			}
			else if (t.getValue().getState().equals("RUNNING")){

				// Get current time when queried using counters for the running task
			}
			Long startTime = t.getValue().getStartTime();
			Float progress = t.getValue().getProgress() *100;
			currentprogress = (double)progress / (double)(finalTime-startTime);
			if(maxprogress<currentprogress){
				maxprogress = currentprogress;
				fastestTask = t.getKey();
			}
		}

		System.out.println("The task ID for the fastest Task: " + fastestTask.toString());
		return fastestTask;

	}
	public ArrayList<TaskID> findSlowTasks(HashMap<TaskID,TaskReport> tasks, Double maxprogress, int killratio){
		Double progressbuffer = maxprogress + (maxprogress * (double)(killratio/100));
		ArrayList<TaskID> slowTasks = new ArrayList<TaskID>();
		Double currentprogress=0.0;
		for(Entry<TaskID,TaskReport> t: tasks.entrySet()){
			if (t.getValue().getState().equals("RUNNING")){
				Long finalTime = System.currentTimeMillis();
				//	Long finalTime = c.getCounter(key);// get the final time for each task
				Long startTime = t.getValue().getStartTime();
				Float progress = t.getValue().getProgress() *100;
				currentprogress = (double)progress / (double)(finalTime-startTime);
				if(currentprogress<progressbuffer){
					slowTasks.add(t.getKey());
				}
			}

		}
		return slowTasks;

	}
	public void printTaskDetails(TaskReport tr){
		System.out.println("~~~~~~Details for Task:~~~~~~");
		System.out.println("Task ID: " + tr.getTaskId());
		System.out.println("Task State: "+ tr.getState());
		//System.out.println("Running Task AttemptID: " + tr.getRunningTaskAttempts().iterator().toString());
		//System.out.println("Successful Task AttemptID: " + tr.getSuccessfulTaskAttempt().toString());
		System.out.println("Start Time: " + tr.getStartTime());
		System.out.println("Progress: " + (float)tr.getProgress()*100);
		System.out.println();
	}
	public static void main(String args[]) throws Exception{
		int exitCode = ToolRunner.run(new CallTaskTrackers(), args);
		System.exit(exitCode);
	}

}