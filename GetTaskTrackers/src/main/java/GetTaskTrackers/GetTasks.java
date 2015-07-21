package GetTaskTrackers;
import java.net.InetSocketAddress;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.TaskCompletionEvent;
import org.apache.hadoop.mapred.TaskID;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class GetTasks implements Tool{

	public static String jobID;
	public static int killratio;
	public Configuration getConf() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setConf(Configuration arg0) {
		// TODO Auto-generated method stub

	}

	public int run(String[] args) throws Exception {
		// TODO Auto-generated method stub
		jobID = args[0];
		killratio= Integer.parseInt(args[1]);
		JobClient theJobClient = new JobClient(new InetSocketAddress("10.2.0.94", 8080), new Configuration());
		@SuppressWarnings("deprecation")
		RunningJob theJob = theJobClient.getJob(jobID); // caution, deprecated
		TaskCompletionEvent[] taskcompletionevents = theJob.getTaskCompletionEvents(0);
		int num_tasks = taskcompletionevents.length;
		if(num_tasks==0){
			System.out.println("There are no tasks running atm!!");
		}
		else{
			for(TaskCompletionEvent t : taskcompletionevents){
				System.out.println("Task ID: "+t.getTaskId() + "Task Attempt ID: " + t.getTaskAttemptId() + "Task Status" + t.getTaskStatus() + "Task Time: " + t.getTaskRunTime());
			}
		}
		TaskID t_id = new TaskID();
		
		return 0;
	}
	public static void main(String args[]) throws Exception{
		int exitCode = ToolRunner.run(new GetTasks(), args);
		System.exit(exitCode);
	}

}
