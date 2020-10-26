package com.jannchie.biliob.service;

import com.jannchie.biliob.constant.DbFields;
import com.jannchie.biliob.model.User;
import com.jannchie.biliob.model.VideoInfo;
import com.jannchie.biliob.model.VideoStat;
import com.jannchie.biliob.model.VideoVisit;
import com.jannchie.biliob.utils.BiliobUtils;
import com.jannchie.biliob.utils.UserUtils;
import com.mongodb.client.MongoClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Controller;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * @author Jannchie
 */
@Controller
public class VideoServiceV3 {
    private static Logger logger = LogManager.getLogger();
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    MongoClient mongoClient;
    @Autowired
    BiliobUtils biliobUtils;

    private void addVideoVisit(Long aid, String type) {
        String finalUserName = biliobUtils.getUserName();
        logger.info("V3：用户[{}]查询aid[{}]的{}数据", finalUserName, aid, type);
        VideoVisit vv = new VideoVisit();
        vv.setAid(aid);
        vv.setDate(Calendar.getInstance().getTime());
        vv.setName(finalUserName);
        mongoTemplate.save(vv);
    }

    private void addVideoVisit(String bvid, String type) {
        String finalUserName = biliobUtils.getUserName();
        logger.info("V3：用户[{}]查询aid[{}]的{}数据", finalUserName, bvid, type);
        VideoVisit vv = new VideoVisit();
        vv.setBvid(bvid);
        vv.setDate(Calendar.getInstance().getTime());
        vv.setName(finalUserName);
        mongoTemplate.save(vv);
    }


    public VideoInfo getVideoInfo(Long aid) {
        Criteria c = Criteria.where("aid").is(aid);
        addVideoVisit(aid, "信息");
        return getVideoInfoByCriteria(c);
    }

    public VideoInfo getVideoInfo(String bvid) {
        Criteria c = Criteria.where("bvid").is(bvid);
        addVideoVisit(bvid, "信息");
        return getVideoInfoByCriteria(c);
    }

    public List<VideoStat> listVideoStat(Long aid) {
        addVideoVisit(aid, "历史");
        Criteria c = Criteria.where("aid").is(aid);
        return getVideoStat(c);
    }

    private List<VideoStat> getVideoStat(Criteria c) {
        return mongoTemplate.find(Query.query(c), VideoStat.class);
    }

    public List<VideoStat> listVideoStat(String bvid) {
        addVideoVisit(bvid, "历史");
        Criteria c = Criteria.where("bvid").is(bvid);
        return getVideoStat(c);
    }


    private VideoInfo getVideoInfoByCriteria(Criteria c) {
        return mongoTemplate.findOne(Query.query(c), VideoInfo.class);
    }

    public Document getAverage(Integer tid, Long mid, Long pubdate) {
        Criteria c = new Criteria();
        if (mid != -1) {
            c.and("owner.mid").is(mid);
        }
        if (tid != -1) {
            c.and("tid").is(tid);
        }
        if (pubdate != -1) {
            c.and("pubdate").lt(pubdate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(c),
                Aggregation.sort(Sort.Direction.DESC, "pubdate"),
                Aggregation.limit(5000),
                Aggregation.group()
                        .avg("stat.view").as("view")
                        .avg("stat.coin").as("coin")
                        .avg("stat.favorite").as("favorite")
                        .avg("stat.reply").as("reply")
                        .avg("stat.danmaku").as("danmaku")
                        .avg("stat.like").as("like")
                        .avg("stat.share").as("share")
        );
        return mongoTemplate.aggregate(aggregation, VideoInfo.class, Document.class).getUniqueMappedResult();
    }

    public List<VideoInfo> listAd() {
        Query q = Query.query(new Criteria().orOperator(Criteria.where(DbFields.OWNER_MID).is(1850091L), Criteria.where(DbFields.MID).is(492106967L)));
        q.with(Sort.by(DbFields.PUBDATE).descending());
        q.limit(5);
        q.fields().include(DbFields.TITLE).include(DbFields.PUBDATE).include(DbFields.OWNER).include(DbFields.STAT);
        return mongoTemplate.find(q, VideoInfo.class);
    }

    public List<VideoInfo> listSearch(String word, Integer page, Integer size, String sort, Long day) {
        if (!Arrays.asList("view", "coin", "reply", "danmaku", "favorite", "like", "share").contains(sort)) {
            sort = "view";
        }
        if (page > 50) {
            page = 50;
        }
        if (size > 20) {
            size = 20;
        }
        Query q = new Query().with(PageRequest.of(page, size, Sort.by("stat." + sort).descending()));
        if (!"".equals(word)) {
            q.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(word.split(" ")));
        }
        if (day != 0) {
            if (day > 30) {
                day = 30L;
            }
            long ctime = Calendar.getInstance().getTimeInMillis() / 1000;
            ctime -= 86400 * day;
            q.addCriteria(Criteria.where("ctime").gt(ctime));
        }
        return mongoTemplate.find(q, VideoInfo.class);
    }

    public List<VideoInfo> listAuthorVideo(Long mid, String sort) {
        if (!Arrays.asList("view", "ctime").contains(sort)) {
            sort = "view";
        }
        return mongoTemplate.find(Query.query(Criteria.where(DbFields.OWNER_MID).is(mid)).with(Sort.by(sort).descending()).limit(10), VideoInfo.class);
    }

    public List<Document> listTopicAuthor(String topic) {
        if ("".equals(topic)) {
            return null;
        }
        return mongoTemplate.aggregate(Aggregation.newAggregation(
                Aggregation.match(TextCriteria.forDefaultLanguage().matching(topic)),
                Aggregation.group(DbFields.OWNER_MID).first(DbFields.OWNER_NAME).as(DbFields.NAME).first(DbFields.OWNER_MID).as(DbFields.MID).first(DbFields.OWNER_FACE).as(DbFields.FACE).sum(DbFields.STAT_VIEW).as(DbFields.VALUE),
                Aggregation.sort(Sort.by(DbFields.VALUE).descending()),
                Aggregation.limit(32)
        ), VideoInfo.class, Document.class).getMappedResults();
    }

    public List<VideoInfo> listFavoriteVideo() {
        User user = UserUtils.getUser();
        if (user == null) {
            return null;
        }
        return mongoTemplate.find(Query.query(Criteria.where(DbFields.AID).in(user.getFavoriteAid())), VideoInfo.class);
    }
}
