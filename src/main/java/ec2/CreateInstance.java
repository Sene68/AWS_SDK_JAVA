package ec2;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.util.ArrayList;
import java.util.List;

/**
 * A 영역의 EC2서버에서 AWS SDK를 통해 B 영역의 EC2에 인스턴스를 생성 하기 위해 역할 전환을 이용해야한다.
 * 아래 코드는 A 영역에서 STSClient를 이용하여 B 영역의 임시 자격증명을 받고 B 영역에 인스턴스를 생성한다.
 */
public class CreateInstance {
    private static final String roleArn = "arn:aws:iam::1234567890:role/role-name"; //iam roleArn
    private static final String roleSessionName = "sene"; //세션 유지할 동안 이름
    private static final int roleSessionTime = 2000; //세션 시간 (초)
    private static final String amiId = "ami-1q2w3e4r5t6t7u"; //ec2 ami ID
    private static final String subnetId = "subnet-0p9o8i7u6y5t"; //서브넷 아이디
    private static final String securityId = "sg-4r5t6y7u8i9o"; //ec2 보안그룹 아이디
    private static final String deviceName = "/dev/xvda"; //ebs 일반적인 경로
    private static int volume = 256; //ebs 용량
    private static final String tagKey = "Name"; //ec2 instance 태그의 Key
    private static final String tagValue = "Test"; //ec2 instance 태그의 Value


    public static void main(String[] args) {
        createAssumeRole();
    }

    private static void createAssumeRole() {
        try (StsClient stsClient = createStsClient()) {
            //역할 전환 role의 Arn을 가지고 임시 자격 증명을 받음
            AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder().roleSessionName(roleSessionName).roleArn(roleArn).durationSeconds(roleSessionTime).build();
            AssumeRoleResponse assumeRoleResponse = stsClient.assumeRole(assumeRoleRequest);

            createInstance(assumeRoleResponse);

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("StsClient does not exist");
        }
    }

    private static void createInstance(AssumeRoleResponse assumeRoleResponse) {

        try (Ec2Client ec2Client = createEc2Client(assumeRoleResponse)) {
            //Set TagSpecification
            List<Tag> tags = new ArrayList<Tag>();
            tags.add(Tag.builder().key(tagKey).value(tagValue).build());

            TagSpecification tagSpecification = TagSpecification.builder().tags(tags).resourceType(ResourceType.INSTANCE).build();

            //Set EBS
            EbsBlockDevice ebsBlockDevice = EbsBlockDevice.builder().deleteOnTermination(true).volumeType(VolumeType.GP2).volumeSize(volume).build();
            BlockDeviceMapping blockDeviceMapping = BlockDeviceMapping.builder().deviceName(deviceName).ebs(ebsBlockDevice).build();

            //Set EC2 Instance
            RunInstancesRequest runRequest = RunInstancesRequest.builder()
                    .imageId(amiId)
                    .instanceType(InstanceType.T2_MICRO)
                    .maxCount(1)
                    .minCount(1)
                    .subnetId(subnetId)
                    .tagSpecifications(tagSpecification)
                    .securityGroupIds(securityId)
                    .blockDeviceMappings(blockDeviceMapping)
                    .build();

            RunInstancesResponse response = ec2Client.runInstances(runRequest);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static Ec2Client createEc2Client(AssumeRoleResponse assumeRoleResponse) {

        //임시 자격증명의 AccessKey, SecretKey, sessionToken을 Credentials로 등록한다.
        Credentials sessionCredentials = assumeRoleResponse.credentials();
        AwsSessionCredentials awsSessionCredentials = AwsSessionCredentials.create(
                sessionCredentials.accessKeyId(),
                sessionCredentials.secretAccessKey(),
                sessionCredentials.sessionToken()
        );

        //EC2Client에는 awsSessionCredentials에 등록한 AccessKey, SecretKey를 사용 (역할 전환)
        return Ec2Client.builder().region(software.amazon.awssdk.regions.Region.AP_NORTHEAST_2).credentialsProvider(StaticCredentialsProvider.create(awsSessionCredentials)).build();
    }

    private static StsClient createStsClient() {
        //IAM의 AccessKey, SecretKey를 로컬에 저장하거나 EC2의 IamRole을 부여하면 별도로 credentialsProvider를 지정 안해도 된다.
        return StsClient.builder().region(Region.AP_NORTHEAST_2).build();
    }
}
