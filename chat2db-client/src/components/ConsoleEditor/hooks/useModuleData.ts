import { useEffect, useState } from 'react';
import sqlService from '@/service/sql';
import { IBoundInfo } from '@/typings';

interface IProps {
  boundInfo: IBoundInfo;
}

export const useModuleData = (props: IProps) => {
  const { boundInfo } = props;
  const [selectedTables, setSelectedTables] = useState<string[]>([]);
  const [tableNameList, setTableNameList] = useState<string[]>([]);

  useEffect(() => {
    const { dataSourceId, databaseName, schemaName } = boundInfo;

    if (!databaseName && !schemaName) {
      setTableNameList([]);
      setSelectedTables([]);
      return;
    }

    sqlService
      .getAllTableList({
        dataSourceId,
        databaseName,
        schemaName,
      })
      .then((data) => {
        const tableNameListTemp = data.map((t) => t.name);
        setTableNameList(tableNameListTemp);

        if (selectedTables.length === 0) {
          setSelectedTables(tableNameListTemp.slice(0, 1));
        }
      });
  }, [boundInfo.dataSourceId, boundInfo.databaseName, boundInfo.schemaName, selectedTables.length]);

  return {
    selectedTables,
    setSelectedTables,
    tableNameList,
    setTableNameList,
  };
};
